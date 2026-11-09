package no.nav.helse.flex.forskuttering

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.forskuttering.domain.NarmesteLederLeesah
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit

class OppdaterForskutteringTest : FellesTestOppsett() {
    @Autowired
    lateinit var forskutteringRepository: ForskutteringRepository

    @Autowired
    lateinit var oppdateringAvForskuttering: OppdateringAvForskuttering

    @Autowired
    lateinit var narmestelederListener: NarmestelederListener

    @Test
    fun `likt og eldre tidsstempel oppdaterer gammel bane som før`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID()).copy(timestamp = OffsetDateTime.parse("2025-01-02T12:00:00Z"))
        oppdateringAvForskuttering.behandleMeldingFraKafka(melding.serialisertTilString())
        val lagret = forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!
        oppdateringAvForskuttering.behandleMeldingFraKafka(
            melding.copy(arbeidsgiverForskutterer = false).serialisertTilString(),
        )
        forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe false
        oppdateringAvForskuttering.behandleMeldingFraKafka(
            melding.copy(timestamp = melding.timestamp.minusDays(1), arbeidsgiverForskutterer = null).serialisertTilString(),
        )
        val oppdatert = forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!
        oppdatert.id shouldBe lagret.id
        oppdatert.arbeidsgiverForskutterer.shouldBeNull()
        oppdatert.timestamp shouldBe melding.timestamp.minusDays(1).toInstant()
    }

    @Test
    fun `Tombstone kvitteres uten å slette eksisterende rad`() {
        forskutteringRepository.deleteAll()
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        oppdateringAvForskuttering.behandleMeldingFraKafka(melding.serialisertTilString())
        val lagret = forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)
        var kvittert = false

        narmestelederListener.listen(
            ConsumerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, 0L, melding.narmesteLederId.toString(), null),
            { kvittert = true },
        )

        kvittert.shouldBeTrue()
        forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId) shouldBe lagret
    }

    @Test
    fun `Melding etter tombstone blir behandlet`() {
        forskutteringRepository.deleteAll()
        val melding = getNarmesteLederLeesah(UUID.randomUUID())

        kafkaProducer
            .send(
                ProducerRecord(NARMESTELEDER_LEESAH_TOPIC, null, melding.narmesteLederId.toString(), null),
            ).get()
        sendNarmesteLederLeesah(melding)

        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId) != null
        }
        forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!.arbeidsgiverForskutterer shouldBe
            melding.arbeidsgiverForskutterer
    }

    @Test
    fun `Melding med ukjent statusfelt blir lagret`() {
        forskutteringRepository.deleteAll()
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val json = melding.serialisertTilString().dropLast(1) + ",\"status\":\"NOE_NYTT\"}"

        oppdateringAvForskuttering.behandleMeldingFraKafka(json)

        forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!.arbeidsgiverForskutterer shouldBe
            melding.arbeidsgiverForskutterer
    }

    @Test
    fun `Oppretter ny forskuttering hvis den ikke finnes fra før`() {
        forskutteringRepository.deleteAll()
        val narmesteLederId = UUID.randomUUID()
        forskutteringRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()

        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)

        forskutteringRepository
            .finnForskuttering(
                narmesteLederLeesah.fnr,
                narmesteLederLeesah.orgnummer,
            )?.arbeidsgiverForskutterer
            .shouldBeNull()

        sendNarmesteLederLeesah(narmesteLederLeesah)

        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = forskutteringRepository.findByNarmesteLederId(narmesteLederId)
        narmesteLeder.shouldNotBeNull()
        narmesteLeder.id.shouldNotBeNull()
        narmesteLeder.narmesteLederId shouldBe narmesteLederLeesah.narmesteLederId
        narmesteLeder.brukerFnr shouldBe narmesteLederLeesah.fnr
        narmesteLeder.orgnummer shouldBe narmesteLederLeesah.orgnummer
        narmesteLeder.aktivFom shouldBe narmesteLederLeesah.aktivFom
        narmesteLeder.aktivTom.shouldBeNull()
        narmesteLeder.arbeidsgiverForskutterer shouldBe narmesteLederLeesah.arbeidsgiverForskutterer
        forskutteringRepository.count() shouldBe 1L

        forskutteringRepository
            .finnForskuttering(
                narmesteLederLeesah.fnr,
                narmesteLederLeesah.orgnummer,
            )?.arbeidsgiverForskutterer!!
            .shouldBeTrue()
    }

    @Test
    fun `Oppdaterer forskuttering hvis den finnes fra før`() {
        forskutteringRepository.deleteAll()
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId, arbeidsgiverForskutterer = true)
        forskutteringRepository
            .finnForskuttering(
                narmesteLederLeesah.fnr,
                narmesteLederLeesah.orgnummer,
            )?.arbeidsgiverForskutterer
            .shouldBeNull()

        sendNarmesteLederLeesah(narmesteLederLeesah)
        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = forskutteringRepository.findByNarmesteLederId(narmesteLederId)!!
        narmesteLeder.arbeidsgiverForskutterer!!.shouldBeTrue()
        forskutteringRepository
            .finnForskuttering(
                narmesteLederLeesah.fnr,
                narmesteLederLeesah.orgnummer,
            )?.arbeidsgiverForskutterer!!
            .shouldBeTrue()

        sendNarmesteLederLeesah(
            getNarmesteLederLeesah(
                narmesteLederId,
                telefonnummer = "98989898",
                epost = "mail@banken.no",
                aktivTom = LocalDate.now(),
                arbeidsgiverForskutterer = false,
            ),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(narmesteLederId)!!.arbeidsgiverForskutterer == false
        }

        forskutteringRepository
            .finnForskuttering(
                narmesteLederLeesah.fnr,
                narmesteLederLeesah.orgnummer,
            )?.arbeidsgiverForskutterer!!
            .shouldBeFalse()

        val oppdaterNl = forskutteringRepository.findByNarmesteLederId(narmesteLederId)!!
        oppdaterNl.id shouldBe narmesteLeder.id
        oppdaterNl.narmesteLederId shouldBe narmesteLederId
        oppdaterNl.aktivTom shouldBe LocalDate.now()
        forskutteringRepository.count() shouldBe 1L
    }

    @Test
    fun `Lagrer og oppdaterer nullable forskutteringsverdier som mottatt`() {
        forskutteringRepository.deleteAll()
        val narmesteLederId = UUID.randomUUID()
        val aktivTom = LocalDate.of(2025, 1, 31)
        val melding =
            getNarmesteLederLeesah(
                narmesteLederId,
                aktivTom = aktivTom,
                arbeidsgiverForskutterer = false,
            )

        sendNarmesteLederLeesah(melding)
        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(narmesteLederId) != null
        }
        val lagret = forskutteringRepository.findByNarmesteLederId(narmesteLederId)!!
        lagret.aktivTom shouldBe aktivTom
        lagret.arbeidsgiverForskutterer shouldBe false

        sendNarmesteLederLeesah(
            melding.copy(
                aktivTom = null,
                arbeidsgiverForskutterer = null,
                timestamp = melding.timestamp.plusSeconds(1),
            ),
        )
        await().atMost(10, TimeUnit.SECONDS).until {
            forskutteringRepository.findByNarmesteLederId(narmesteLederId)?.aktivTom == null
        }
        val oppdatert = forskutteringRepository.findByNarmesteLederId(narmesteLederId)!!
        oppdatert.id shouldBe lagret.id
        oppdatert.aktivTom.shouldBeNull()
        oppdatert.arbeidsgiverForskutterer.shouldBeNull()
        forskutteringRepository.count() shouldBe 1L
    }

    @Test
    fun `Nyere melding oppdaterer eksisterende rad`() {
        forskutteringRepository.deleteAll()
        val melding = getNarmesteLederLeesah(UUID.randomUUID()).copy(timestamp = OffsetDateTime.parse("2025-01-02T12:00:00Z"))
        oppdateringAvForskuttering.behandleMeldingFraKafka(melding.serialisertTilString())
        val lagret = forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!
        val nyere = melding.copy(arbeidsgiverForskutterer = false, timestamp = melding.timestamp.plusSeconds(1))

        oppdateringAvForskuttering.behandleMeldingFraKafka(nyere.serialisertTilString())

        val oppdatert = forskutteringRepository.findByNarmesteLederId(melding.narmesteLederId)!!
        oppdatert.id shouldBe lagret.id
        oppdatert.timestamp shouldBe nyere.timestamp.toInstant()
        oppdatert.arbeidsgiverForskutterer shouldBe false
    }

    fun sendNarmesteLederLeesah(
        nl: NarmesteLederLeesah,
        topic: String = NARMESTELEDER_LEESAH_TOPIC,
        partition: Int? = null,
    ) {
        kafkaProducer
            .send(
                ProducerRecord(
                    topic,
                    partition,
                    nl.narmesteLederId.toString(),
                    nl.serialisertTilString(),
                ),
            ).get()
    }
}

fun getNarmesteLederLeesah(
    narmesteLederId: UUID,
    telefonnummer: String = "90909090",
    epost: String = "test@nav.no",
    aktivTom: LocalDate? = null,
    fnr: String = "12345678910",
    orgnummer: String = "999999999",
    arbeidsgiverForskutterer: Boolean? = true,
): NarmesteLederLeesah =
    NarmesteLederLeesah(
        narmesteLederId = narmesteLederId,
        fnr = fnr,
        orgnummer = orgnummer,
        narmesteLederFnr = "01987654321",
        narmesteLederTelefonnummer = telefonnummer,
        narmesteLederEpost = epost,
        aktivFom = LocalDate.now(),
        aktivTom = aktivTom,
        arbeidsgiverForskutterer = arbeidsgiverForskutterer,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
    )
