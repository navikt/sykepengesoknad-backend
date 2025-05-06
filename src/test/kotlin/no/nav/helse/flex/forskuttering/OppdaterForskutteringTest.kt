package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.forskuttering.domain.NarmesteLederLeesah
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
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
        narmesteLeder.aktivTom.shouldBeNull()

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
        oppdaterNl.aktivTom `should be equal to` LocalDate.now()
    }

    fun sendNarmesteLederLeesah(nl: NarmesteLederLeesah) {
        kafkaProducer
            .send(
                ProducerRecord(
                    NARMESTELEDER_LEESAH_TOPIC,
                    null,
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
    orgnummer: String = "999999",
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
