package no.nav.helse.flex.forskuttering

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    @Autowired lateinit var leser: ForskutteringLeser

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    @Autowired lateinit var meterRegistry: MeterRegistry

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

    private fun måltOffset(
        registry: MeterRegistry,
        partisjon: Int,
    ): Double? =
        registry
            .find("forskuttering_esyfo_checkpoint_offset")
            .tags("topic", NARMESTELEDER_ESYFO_LEESAH_TOPIC, "partition", partisjon.toString())
            .gauge()
            ?.value()

    @Test
    fun `checkpointmåleren leser varig DB-posisjon etter restart duplikat og rollback`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val key = melding.narmesteLederId.toString()
        val partisjon = 19
        oppdatering.behandle(record(10, key, melding.serialisertTilString(), partisjon))

        val nyttRegister = SimpleMeterRegistry()
        val etterRestart = ForskutteringEsyfoCheckpointMetrikk(jdbc, nyttRegister)
        etterRestart.registrerEksisterende()
        måltOffset(nyttRegister, partisjon) shouldBe 10.0
        etterRestart.registrerEksisterende()
        oppdatering.behandle(record(10, key, null, partisjon)) shouldBe "duplikat"
        måltOffset(nyttRegister, partisjon) shouldBe 10.0

        assertThrows<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                oppdatering.behandle(record(11, key, null, partisjon))
                error("Test: DB-transaksjonen rulles tilbake")
            }
        }
        checkpoint(partisjon) shouldBe 10L
        måltOffset(nyttRegister, partisjon) shouldBe 10.0

        var kvittert = false
        esyfoListener.listen(record(12, key, null, partisjon)) { kvittert = true }
        kvittert shouldBe true
        måltOffset(meterRegistry, partisjon) shouldBe 12.0
        måltOffset(nyttRegister, partisjon) shouldBe 12.0
    }

    @Test
    fun `ny tabell er isolert og kildevalg gir bare én tabell`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID(), fnr = "12345678942", orgnummer = "999999991")
        val key = melding.narmesteLederId.toString()
        listener.listen(
            ConsumerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, 0, key, melding.serialisertTilString()),
        ) { }
        oppdatering.behandle(record(10, key, melding.copy(arbeidsgiverForskutterer = false).serialisertTilString(), 10))

        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe false
        leser.lesekilde shouldBe ForskutteringLesekilde.GAMMEL
        leser.finnForskuttering(melding.fnr, melding.orgnummer) shouldBe true
        ForskutteringLeser(gammel, esyfo, SimpleMeterRegistry(), "ESYFO", true)
            .finnForskuttering(melding.fnr, melding.orgnummer) shouldBe false
        assertThrows<IllegalArgumentException> { ForskutteringLeser(gammel, esyfo, SimpleMeterRegistry(), "ESYFO", false) }
        assertThrows<IllegalArgumentException> { ForskutteringLeser(gammel, null, SimpleMeterRegistry(), "ESYFO", true) }
        assertThrows<IllegalArgumentException> { ForskutteringLeser(gammel, esyfo, SimpleMeterRegistry(), "ANNET", true) }
        oppdatering.behandle(record(11, key, null, 10))
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
    }

    @Test
    fun `begge kilder prioriterer null aktiv tom ved flere relasjoner`() {
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
        esyfo.finnForskuttering(aktiv.fnr, aktiv.orgnummer)?.narmesteLederId shouldBe aktiv.narmesteLederId
        leser.finnForskuttering(aktiv.fnr, aktiv.orgnummer) shouldBe true
        ForskutteringLeser(gammel, esyfo, SimpleMeterRegistry(), "ESYFO", true)
            .finnForskuttering(aktiv.fnr, aktiv.orgnummer) shouldBe false
    }

    @Test
    fun `tombstone flytter checkpoint og gammel offset gjenoppretter ikke slettet rad`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val key = melding.narmesteLederId.toString()
        val partition = 11
        oppdatering.behandle(record(20, key, melding.serialisertTilString(), partition)) shouldBe "oppdatert"
        oppdatering.behandle(record(22, key, null, partition)) shouldBe "slettet"
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint(partition) shouldBe 22L
        oppdatering.behandle(record(20, key, melding.serialisertTilString(), partition)) shouldBe "duplikat"
        oppdatering.behandle(record(22, key, melding.serialisertTilString(), partition)) shouldBe "duplikat"
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
    fun `høyere offset vinner også med eldre og likt domenetidsstempel`() {
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
    fun `ukjente felter og status tåles men feil key avvises uten ack`() {
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
        esyfoListener.listen(record(1, melding.narmesteLederId.toString(), json, 13)) { kvittert = true }
        kvittert shouldBe true
        kvittert = false
        assertThrows<IllegalStateException> {
            esyfoListener.listen(record(2, UUID.randomUUID().toString(), json, 13)) { kvittert = true }
        }
        kvittert shouldBe false
        checkpoint(13) shouldBe 1L
        assertThrows<IllegalArgumentException> { oppdatering.behandle(record(2, "ikke-uuid", null, 13)) }
        checkpoint(13) shouldBe 1L
    }

    @Test
    fun `feil etter domeneskriving ruller tilbake og retry kan committe`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val rec = record(1, melding.narmesteLederId.toString(), melding.serialisertTilString(), 14)
        assertThrows<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                oppdatering.behandle(rec)
                error("Test feil før ytre commit")
            }
        }
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint(14).shouldBeNull()
        var kvittert = false
        esyfoListener.listen(rec) { kvittert = true }
        kvittert shouldBe true
        esyfoListener.listen(rec) { kvittert = true }
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
            assertThrows<IllegalStateException> { esyfoListener.listen(rec) { kvittert = true } }
            kvittert shouldBe false
            esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
            checkpoint(18).shouldBeNull()
        } finally {
            jdbc.execute("DROP TRIGGER test_forskuttering_esyfo_checkpoint_feil ON forskuttering_esyfo_checkpoint")
            jdbc.execute("DROP FUNCTION test_forskuttering_esyfo_checkpoint_feil()")
        }
        esyfoListener.listen(rec) { kvittert = true }
        kvittert shouldBe true
        checkpoint(18) shouldBe 1L
        esyfo.findByNarmesteLederId(melding.narmesteLederId)?.narmesteLederId shouldBe melding.narmesteLederId
    }

    @Test
    fun `replay etter DB commit men før Kafka ack endrer ikke raden`() {
        val melding = getNarmesteLederLeesah(UUID.randomUUID())
        val rec = record(1, melding.narmesteLederId.toString(), melding.serialisertTilString(), 17)
        assertThrows<IllegalStateException> {
            esyfoListener.listen(rec) { error("Simulerer manglende Kafka-ack") }
        }
        val lagret = esyfo.findByNarmesteLederId(melding.narmesteLederId)
        checkpoint(17) shouldBe 1L

        var kvittert = false
        esyfoListener.listen(rec) { kvittert = true }
        kvittert shouldBe true
        esyfo.findByNarmesteLederId(melding.narmesteLederId) shouldBe lagret
    }

    @Test
    fun `samtidig første checkpoint låser og tidligere record på annen key blir ikke hoppet over`() {
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
