package no.nav.helse.flex.forskuttering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ForskutteringEsyfoTest : FellesTestOppsett() {
    @Autowired lateinit var esyfo: ForskutteringEsyfoRepository

    @Autowired lateinit var gammel: ForskutteringRepository

    @Autowired lateinit var oppdatering: OppdateringAvForskutteringEsyfo

    @Autowired lateinit var gammelOppdatering: OppdateringAvForskuttering

    @Autowired lateinit var listener: NarmestelederListener

    @Autowired lateinit var esyfoListener: NarmestelederEsyfoListener

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private fun record(
        offset: Long,
        key: String,
        verdi: String?,
        partition: Int = 0,
    ) = ConsumerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, partition, offset, key, verdi)

    private fun checkpoint(partition: Int): Long? =
        jdbc
            .query(
                "SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint WHERE topic = ? AND partition = ?",
                { rs, _ -> rs.getLong(1) },
                NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                partition,
            ).firstOrNull()

    @Test
    fun `Batch lagres atomisk med én kvittering og bevarer rekkefølge og tombstones`() {
        val første = getNarmesteLederLeesah(UUID.randomUUID())
        val andre = getNarmesteLederLeesah(UUID.randomUUID())
        val førsteKey = første.narmesteLederId.toString()
        val andreKey = andre.narmesteLederId.toString()
        val batch =
            listOf(
                record(0, førsteKey, første.serialisertTilString(), 21),
                record(0, andreKey, andre.serialisertTilString(), 20),
                record(1, førsteKey, null, 21),
            )
        var kvitteringer = 0
        esyfoListener.listen(batch) {
            esyfo.findByNarmesteLederId(første.narmesteLederId).shouldBeNull()
            esyfo.findByNarmesteLederId(andre.narmesteLederId)?.narmesteLederId shouldBe andre.narmesteLederId
            checkpoint(20) shouldBe 0L
            checkpoint(21) shouldBe 1L
            kvitteringer++
        }
        kvitteringer shouldBe 1
        val lagret = esyfo.findByNarmesteLederId(andre.narmesteLederId)
        esyfoListener.listen(batch) { kvitteringer++ }
        kvitteringer shouldBe 2
        esyfo.findByNarmesteLederId(andre.narmesteLederId) shouldBe lagret
    }

    @Test
    fun `Feil i batch ruller tilbake alle partisjoner uten kvittering og kan forsøkes igjen`() {
        val første = getNarmesteLederLeesah(UUID.randomUUID())
        val andre = getNarmesteLederLeesah(UUID.randomUUID())
        val førsteKey = første.narmesteLederId.toString()
        val andreKey = andre.narmesteLederId.toString()
        var kvitteringer = 0
        val feil =
            shouldThrow<IllegalStateException> {
                esyfoListener.listen(
                    listOf(record(0, førsteKey, første.serialisertTilString(), 22), record(0, andreKey, "{", 23)),
                ) { kvitteringer++ }
            }
        feil.cause.shouldBeNull()
        kvitteringer shouldBe 0
        esyfo.findByNarmesteLederId(første.narmesteLederId).shouldBeNull()
        checkpoint(22).shouldBeNull()
        checkpoint(23).shouldBeNull()

        esyfoListener.listen(
            listOf(record(0, andreKey, andre.serialisertTilString(), 23), record(0, førsteKey, første.serialisertTilString(), 22)),
        ) { kvitteringer++ }
        kvitteringer shouldBe 1
        checkpoint(22) shouldBe 0L
        checkpoint(23) shouldBe 0L
    }

    @Test
    fun `Tom batch kvitteres ikke`() {
        var kvitteringer = 0
        esyfoListener.listen(emptyList()) { kvitteringer++ }
        kvitteringer shouldBe 0
    }

    @Test
    fun `Innlesing fra eSyfo lagres i separat tabell uten å endre eksisterende forskuttering`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID(), fnr = "12345678942", orgnummer = "999999991")
        val key = melding.narmesteLederId.toString()
        listener.listen(
            ConsumerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, 0, key, melding.serialisertTilString()),
        ) { }
        oppdatering.behandle(record(10, key, melding.copy(arbeidsgiverForskutterer = false).serialisertTilString(), 10))

        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe false
        oppdatering.behandle(record(11, key, null, 10))
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
    }

    @Test
    fun `Innlesing fra eSyfo lagrer aktive og avsluttede relasjoner uten å endre eksisterende oppslag`() {
        val aktiv = getNarmesteLederLeesah(UUID.randomUUID(), fnr = "12345678943", orgnummer = "999999992")
        val avsluttet =
            getNarmesteLederLeesah(
                UUID.randomUUID(),
                fnr = aktiv.fnr,
                orgnummer = aktiv.orgnummer,
                aktivTom = LocalDate.of(2025, 1, 1),
                arbeidsgiverForskutterer = false,
            )
        gammelOppdatering.behandleMeldingFraKafka(aktiv.serialisertTilString())
        gammelOppdatering.behandleMeldingFraKafka(avsluttet.serialisertTilString())
        oppdatering.behandle(
            record(1, aktiv.narmesteLederId.toString(), aktiv.copy(arbeidsgiverForskutterer = false).serialisertTilString(), 16),
        )
        oppdatering.behandle(
            record(2, avsluttet.narmesteLederId.toString(), avsluttet.copy(arbeidsgiverForskutterer = true).serialisertTilString(), 16),
        )

        gammel.finnForskuttering(aktiv.fnr, aktiv.orgnummer)?.narmesteLederId shouldBe aktiv.narmesteLederId
        esyfo.findByNarmesteLederId(aktiv.narmesteLederId)?.arbeidsgiverForskutterer shouldBe false
        esyfo.findByNarmesteLederId(avsluttet.narmesteLederId)?.aktivTom shouldBe avsluttet.aktivTom
        gammel.finnForskuttering(aktiv.fnr, aktiv.orgnummer)?.arbeidsgiverForskutterer shouldBe true
    }

    @Test
    fun `Tombstone flytter checkpoint og gammel offset gjenoppretter ikke slettet rad`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val key = melding.narmesteLederId.toString()
        val partition = 11
        oppdatering.behandle(record(20, key, melding.serialisertTilString(), partition)) shouldBe true
        oppdatering.behandle(record(22, key, null, partition)) shouldBe true
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint(partition) shouldBe 22L
        oppdatering.behandle(record(20, key, melding.serialisertTilString(), partition)) shouldBe false
        oppdatering.behandle(record(22, key, melding.serialisertTilString(), partition)) shouldBe false
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        oppdatering.behandle(
            record(24, key, melding.copy(arbeidsgiverForskutterer = null, aktivTom = null).serialisertTilString(), partition),
        )
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer.shouldBeNull()
        checkpoint(partition) shouldBe 24L
        val annen = getNarmesteLederLeesah(UUID.randomUUID())
        oppdatering.behandle(record(25, annen.narmesteLederId.toString(), null, partition))
        checkpoint(partition) shouldBe 25L
    }

    @Test
    fun `Høyere offset vinner også med eldre og likt domenetidsstempel`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID()).copy(timestamp = OffsetDateTime.parse("2025-01-02T12:00:00Z"))
        val key = melding.narmesteLederId.toString()
        oppdatering.behandle(record(1, key, melding.serialisertTilString(), 12))
        oppdatering.behandle(record(2, key, melding.copy(arbeidsgiverForskutterer = false).serialisertTilString(), 12))
        oppdatering.behandle(
            record(3, key, melding.copy(timestamp = melding.timestamp.minusDays(1), aktivTom = LocalDate.now()).serialisertTilString(), 12),
        )
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.aktivTom shouldBe LocalDate.now()
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
    }

    @Test
    fun `Ukjente felter og status tåles men feil key avvises uten ack`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val json =
            """
            {
              "narmesteLederId":"${melding.narmesteLederId}",
              "fnr":"${melding.fnr}",
              "orgnummer":"${melding.orgnummer}",
              "aktivFom":"2025-01-01",
              "aktivTom":null,
              "arbeidsgiverForskutterer":true,
              "timestamp":"2025-01-02T12:00:00Z",
              "status":"NY",
              "ukjent":1
            }
            """.trimIndent()
        var kvittert = false
        esyfoListener.listen(listOf(record(1, melding.narmesteLederId.toString(), json, 13))) { kvittert = true }
        kvittert shouldBe true
        kvittert = false
        shouldThrow<IllegalStateException> {
            esyfoListener.listen(listOf(record(2, UUID.randomUUID().toString(), json, 13))) { kvittert = true }
        }
        kvittert shouldBe false
        checkpoint(13) shouldBe 1L
        listOf(null, "ugyldig-uuid").forEach { key ->
            val feil =
                shouldThrow<IllegalStateException> {
                    esyfoListener.listen(listOf(ConsumerRecord<String, String?>(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 13, 2L, key, null))) {
                        kvittert = true
                    }
                }
            feil.cause.shouldBeNull()
            kvittert shouldBe false
        }
        checkpoint(13) shouldBe 1L
    }

    @Test
    fun `Feil etter domeneskriving ruller tilbake og retry kan committe`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val rec = record(1, melding.narmesteLederId.toString(), melding.serialisertTilString(), 14)
        shouldThrow<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                oppdatering.behandle(rec)
                error("Test feil før ytre commit")
            }
        }
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint(14).shouldBeNull()
        var kvittert = false
        esyfoListener.listen(listOf(rec)) { kvittert = true }
        kvittert shouldBe true
        esyfoListener.listen(listOf(rec)) { kvittert = true }
        checkpoint(14) shouldBe 1L
    }

    @Test
    fun `DB feil mellom domeneskriving og checkpoint ruller tilbake uten ack`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val rec = record(1, melding.narmesteLederId.toString(), melding.serialisertTilString(), 18)
        jdbc.execute(
            """
            CREATE FUNCTION test_forskuttering_esyfo_checkpoint_feil() RETURNS trigger AS ${'$'}${'$'}
            BEGIN
                IF NEW.partition = 18 THEN
                    RAISE EXCEPTION 'Test: checkpoint kan ikke oppdateres';
                END IF;
                RETURN NEW;
            END;
            ${'$'}${'$'} LANGUAGE plpgsql
            """.trimIndent(),
        )
        jdbc.execute(
            """CREATE TRIGGER test_forskuttering_esyfo_checkpoint_feil
               BEFORE UPDATE ON forskuttering_esyfo_checkpoint
               FOR EACH ROW EXECUTE FUNCTION test_forskuttering_esyfo_checkpoint_feil()""",
        )
        var kvittert = false
        try {
            shouldThrow<IllegalStateException> { esyfoListener.listen(listOf(rec)) { kvittert = true } }
            kvittert shouldBe false
            esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
            checkpoint(18).shouldBeNull()
        } finally {
            jdbc.execute("DROP TRIGGER test_forskuttering_esyfo_checkpoint_feil ON forskuttering_esyfo_checkpoint")
            jdbc.execute("DROP FUNCTION test_forskuttering_esyfo_checkpoint_feil()")
        }
        esyfoListener.listen(listOf(rec)) { kvittert = true }
        kvittert shouldBe true
        checkpoint(18) shouldBe 1L
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.narmesteLederId shouldBe melding.narmesteLederId
    }

    @Test
    fun `Replay etter DB commit men før Kafka ack endrer ikke raden`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val rec = record(1, melding.narmesteLederId.toString(), melding.serialisertTilString(), 17)
        shouldThrow<IllegalStateException> {
            esyfoListener.listen(listOf(rec)) { error("Simulerer manglende Kafka-ack") }
        }
        val lagret = esyfo.findByNarmesteLederId(melding.narmesteLederId)
        checkpoint(17) shouldBe 1L

        var kvittert = false
        esyfoListener.listen(listOf(rec)) { kvittert = true }
        kvittert shouldBe true
        esyfo.findByNarmesteLederId(melding.narmesteLederId) shouldBe lagret
    }

    @Test
    fun `Samtidig første checkpoint låser og tidligere record på annen key blir ikke hoppet over`() {
        val a = getNarmesteLederLeesah(UUID.randomUUID())
        val b = getNarmesteLederLeesah(UUID.randomUUID())
        val pool = Executors.newFixedThreadPool(2)
        val skrevet = CountDownLatch(1)
        val frigi = CountDownLatch(1)
        val andreStartet = CountDownLatch(1)
        try {
            val første =
                pool.submit {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        oppdatering.behandle(record(1, a.narmesteLederId.toString(), a.serialisertTilString(), 15))
                        skrevet.countDown()
                        check(frigi.await(10, TimeUnit.SECONDS))
                    }
                }
            check(skrevet.await(10, TimeUnit.SECONDS))
            val andre =
                pool.submit {
                    andreStartet.countDown()
                    oppdatering.behandle(record(2, b.narmesteLederId.toString(), b.serialisertTilString(), 15))
                }
            check(andreStartet.await(10, TimeUnit.SECONDS))
            check(!andre.isDone)
            frigi.countDown()
            første.get(10, TimeUnit.SECONDS)
            andre.get(10, TimeUnit.SECONDS)
            esyfo.findByNarmesteLederId(a.narmesteLederId)?.narmesteLederId shouldBe a.narmesteLederId
            esyfo.findByNarmesteLederId(b.narmesteLederId)?.narmesteLederId shouldBe b.narmesteLederId
            checkpoint(15) shouldBe 2L
        } finally {
            frigi.countDown()
            pool.shutdownNow()
        }
    }
}
