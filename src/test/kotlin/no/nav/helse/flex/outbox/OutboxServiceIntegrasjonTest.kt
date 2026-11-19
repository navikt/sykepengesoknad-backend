package no.nav.helse.flex.outbox

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.domain.EventType
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.SporingType
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testoppsett.PausbarOutboxService
import no.nav.helse.flex.tilJuridiskVurdering
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class OutboxServiceIntegrasjonTest : FellesTestOppsett() {
    @Autowired
    private lateinit var outboxService: OutboxServiceImpl

    @Autowired
    private lateinit var outboxDbRepository: OutboxDbRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var pausbarOutboxService: PausbarOutboxService

    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        pausbarOutboxService.pauseJobb()
        transactionTemplate = TransactionTemplate(transactionManager)
        outboxDbRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        outboxDbRepository.deleteAll()
        pausbarOutboxService.startJobb()
    }

    @Test
    fun `sender sykepengesøknad fra outbox`() {
        val soknad =
            SykepengesoknadDTO(
                id = UUID.randomUUID().toString(),
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.NY,
                fnr = "11111111111",
                fom = LocalDate.of(2025, 1, 1),
                tom = LocalDate.of(2025, 1, 31),
            )

        transactionTemplate.executeWithoutResult { outboxService.outboxSykepengesoknad(soknad) }

        outboxDbRepository.findAll().single().also {
            it.type shouldBe OutboxType.SYKEPENGESOKNAD
            it.fnr shouldBe "11111111111"
            it.sendtTimestamp.shouldBeNull()
        }

        outboxService.sendEldsteUsendte() shouldBe true

        outboxDbRepository
            .findAll()
            .single()
            .sendtTimestamp
            .shouldNotBeNull()
        val records = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        records.single().key() shouldBe soknad.id
        records.tilSoknader().single() shouldBe soknad
    }

    @Test
    fun `sender juridisk vurdering fra outbox`() {
        val juridiskVurdering =
            JuridiskVurdering(
                fodselsnummer = "22222222222",
                sporing = mapOf(SporingType.SOKNAD to listOf("soknad-1")),
                input = mapOf("fom" to LocalDate.of(2025, 1, 1), "antall" to 3),
                output = mapOf("resultat" to true),
                lovverk = "folketrygdloven",
                paragraf = "8-17",
                ledd = 1,
                bokstav = "a",
                punktum = null,
                lovverksversjon = LocalDate.of(2018, 1, 1),
                utfall = Utfall.VILKAR_BEREGNET,
            )

        transactionTemplate.executeWithoutResult { outboxService.outboxJuridiskVurdering(juridiskVurdering) }

        outboxDbRepository.findAll().single().also {
            it.type shouldBe OutboxType.JURIDISK_VURDERING
            it.fnr shouldBe "22222222222"
        }

        outboxService.sendEldsteUsendte() shouldBe true

        val vurdering = juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1).tilJuridiskVurdering().single()
        vurdering.fodselsnummer shouldBe "22222222222"
        vurdering.sporing shouldBe mapOf(SporingType.SOKNAD to listOf("soknad-1"))
        vurdering.input shouldBe mapOf("fom" to "2025-01-01", "antall" to 3)
        vurdering.output shouldBe mapOf("resultat" to true)
        vurdering.utfall shouldBe Utfall.VILKAR_BEREGNET
    }

    @Test
    fun `sender auditlogg fra outbox med egen fnr-nøkkel per rad`() {
        val auditEntry =
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = "Z123456",
                oppslagPå = "33333333333",
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = Instant.now().truncatedTo(ChronoUnit.MICROS),
                beskrivelse = "Henter søknader",
                requestUrl = URI.create("http://localhost/api/v1/flex/sykepengesoknader"),
                requestMethod = "POST",
            )

        transactionTemplate.executeWithoutResult {
            outboxService.outboxAuditEntry(auditEntry)
            outboxService.outboxAuditEntry(auditEntry)
        }

        val rader = outboxDbRepository.findAll().toList()
        rader shouldHaveSize 2
        rader.map { it.fnr }.toSet() shouldHaveSize 2
        rader.forEach { it.fnr.startsWith("audit-") shouldBe true }

        outboxService.sendEldsteUsendte() shouldBe true
        outboxService.sendEldsteUsendte() shouldBe true
        outboxService.sendEldsteUsendte() shouldBe false

        auditlogKafkaConsumer.ventPåRecords(antall = 2).forEach {
            objectMapper.readValue<AuditEntry>(it.value()) shouldBe auditEntry
        }
    }

    @Test
    fun `rebehandling lagres i egen transaksjon som overlever rollback`() {
        val sykmeldingKafkaMessage = sykmeldingKafkaMessage(fnr = "44444444444")
        val behandlingstidspunkt = OffsetDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)

        transactionTemplate.executeWithoutResult { status ->
            outboxService.outboxRebehandlingSykmelding(sykmeldingKafkaMessage, behandlingstidspunkt)
            status.setRollbackOnly()
        }

        outboxDbRepository.findAll().single().also {
            it.type shouldBe OutboxType.REBEHANDLING_SYKMELDING
            it.fnr shouldBe "44444444444"
        }

        outboxService.sendEldsteUsendte() shouldBe true

        val meldingCaptor = argumentCaptor<SykmeldingKafkaMessageDTO>()
        val tidspunktCaptor = argumentCaptor<OffsetDateTime>()
        verify(rebehandlingsSykmeldingSendtProducer).leggPaRebehandlingTopic(meldingCaptor.capture(), tidspunktCaptor.capture())
        meldingCaptor.firstValue.event.sykmeldingId shouldBe sykmeldingKafkaMessage.event.sykmeldingId
        meldingCaptor.firstValue.kafkaMetadata.fnr shouldBe "44444444444"
        tidspunktCaptor.firstValue.isEqual(behandlingstidspunkt) shouldBe true
    }

    @Test
    fun `skriving til outbox krever eksisterende transaksjon`() {
        assertThrows<IllegalTransactionStateException> {
            outboxService.outboxSykepengesoknad(
                SykepengesoknadDTO(
                    id = UUID.randomUUID().toString(),
                    type = SoknadstypeDTO.ARBEIDSTAKERE,
                    status = SoknadsstatusDTO.NY,
                    fnr = "11111111111",
                ),
            )
        }

        outboxDbRepository.findAll().toList().shouldBeEmpty()
    }

    @Test
    fun `rad som feiler blir ikke markert som sendt`() {
        val sykmeldingKafkaMessage = sykmeldingKafkaMessage(fnr = "55555555555")
        transactionTemplate.executeWithoutResult {
            outboxService.outboxRebehandlingSykmelding(sykmeldingKafkaMessage, OffsetDateTime.now())
        }
        doThrow(RuntimeException("feil")).whenever(rebehandlingsSykmeldingSendtProducer).leggPaRebehandlingTopic(any(), any())

        assertThrows<RuntimeException> { outboxService.sendEldsteUsendte() }

        outboxDbRepository
            .findAll()
            .single()
            .sendtTimestamp
            .shouldBeNull()
    }

    @Test
    fun `returnerer false når det ikke er noe å sende`() {
        outboxService.sendEldsteUsendte() shouldBe false
        verify(rebehandlingsSykmeldingSendtProducer, never()).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `låser eldste usendte rad`() {
        val nå = Instant.now()
        lagreUsendtRad(fnr = "fnr-nyest", opprettetTimestamp = nå.minusSeconds(10))
        val eldste = lagreUsendtRad(fnr = "fnr-eldst", opprettetTimestamp = nå.minusSeconds(60))
        lagreUsendtRad(fnr = "fnr-eldst", opprettetTimestamp = nå.minusSeconds(30))

        val rad = transactionTemplate.execute { outboxDbRepository.finnEldsteLedigeRadOgLas() }

        rad?.id shouldBe eldste.id
    }

    @Test
    fun `sender én rad per kall, eldste først på tvers av fnr`() {
        val a1 = sykmeldingKafkaMessage(fnr = "88888888881")
        val b1 = sykmeldingKafkaMessage(fnr = "88888888882")
        val a2 = sykmeldingKafkaMessage(fnr = "88888888881")
        listOf(a1, b1, a2).forEach { outboxService.outboxRebehandlingSykmelding(it, OffsetDateTime.now()) }

        val sendte = mutableListOf<String>()
        doAnswer { invocation ->
            sendte.add(invocation.getArgument<SykmeldingKafkaMessageDTO>(0).event.sykmeldingId)
            Unit
        }.whenever(rebehandlingsSykmeldingSendtProducer).leggPaRebehandlingTopic(any(), any())

        outboxService.sendEldsteUsendte() shouldBe true
        sendte shouldBe listOf(a1.event.sykmeldingId)

        outboxService.sendEldsteUsendte() shouldBe true
        sendte shouldBe listOf(a1.event.sykmeldingId, b1.event.sykmeldingId)

        outboxService.sendEldsteUsendte() shouldBe true
        sendte shouldBe listOf(a1.event.sykmeldingId, b1.event.sykmeldingId, a2.event.sykmeldingId)

        outboxService.sendEldsteUsendte() shouldBe false
    }

    @Test
    fun `låst rad hindrer at nyere rader for samme fnr plukkes av en annen transaksjon`() {
        val nå = Instant.now()
        val x1 = lagreUsendtRad(fnr = "fnr-x", opprettetTimestamp = nå.minusSeconds(60))
        lagreUsendtRad(fnr = "fnr-x", opprettetTimestamp = nå.minusSeconds(50))
        val y1 = lagreUsendtRad(fnr = "fnr-y", opprettetTimestamp = nå.minusSeconds(30))

        var låstAvTransaksjon1: OutboxDbRecord? = null
        val låstAvTransaksjon2 =
            medÅpenTransaksjonIAnnenTråd(
                iTråd = { låstAvTransaksjon1 = outboxDbRepository.finnEldsteLedigeRadOgLas() },
                iMellomtiden = { transactionTemplate.execute { outboxDbRepository.finnEldsteLedigeRadOgLas() } },
            )

        låstAvTransaksjon1?.id shouldBe x1.id
        låstAvTransaksjon2?.id shouldBe y1.id
    }

    @Test
    fun `returnerer ingen rad når eldste rad for eneste fnr er låst`() {
        val nå = Instant.now()
        val x1 = lagreUsendtRad(fnr = "fnr-x", opprettetTimestamp = nå.minusSeconds(60))
        lagreUsendtRad(fnr = "fnr-x", opprettetTimestamp = nå.minusSeconds(50))

        var låstAvTransaksjon1: OutboxDbRecord? = null
        val låstAvTransaksjon2 =
            medÅpenTransaksjonIAnnenTråd(
                iTråd = { låstAvTransaksjon1 = outboxDbRepository.finnEldsteLedigeRadOgLas() },
                iMellomtiden = { transactionTemplate.execute { outboxDbRepository.finnEldsteLedigeRadOgLas() } },
            )

        låstAvTransaksjon1?.id shouldBe x1.id
        låstAvTransaksjon2.shouldBeNull()
    }

    @Test
    fun `samme rad sendes ikke flere ganger når flere instanser sender samtidig`() {
        val antallRader = 300
        val antallInstanser = 4
        repeat(antallRader) { i ->
            outboxService.outboxRebehandlingSykmelding(
                sykmeldingKafkaMessage(fnr = (20000000000L + i).toString()),
                OffsetDateTime.now(),
            )
        }

        val sendteSykmeldingIder = ConcurrentLinkedQueue<String>()
        doAnswer { invocation ->
            sendteSykmeldingIder.add(invocation.getArgument<SykmeldingKafkaMessageDTO>(0).event.sykmeldingId)
            Unit
        }.whenever(rebehandlingsSykmeldingSendtProducer).leggPaRebehandlingTopic(any(), any())

        val start = CountDownLatch(1)
        val feil = ConcurrentLinkedQueue<Throwable>()
        val tråder =
            (1..antallInstanser).map {
                thread {
                    try {
                        start.await()
                        while (outboxService.sendEldsteUsendte()) {
                            // Fortsetter til det ikke er flere ledige fnr
                        }
                    } catch (e: Throwable) {
                        feil.add(e)
                    }
                }
            }
        start.countDown()
        tråder.forEach { it.join(TimeUnit.SECONDS.toMillis(60)) }

        feil.shouldBeEmpty()
        outboxDbRepository.findAll().filter { it.sendtTimestamp == null }.shouldBeEmpty()
        sendteSykmeldingIder.groupingBy { it }.eachCount().filterValues { it > 1 } shouldBe emptyMap()
        sendteSykmeldingIder shouldHaveSize antallRader
    }

    private fun <T> medÅpenTransaksjonIAnnenTråd(
        iTråd: () -> Unit,
        iMellomtiden: () -> T,
    ): T {
        val harLås = CountDownLatch(1)
        val slipp = CountDownLatch(1)
        val tråd =
            thread {
                transactionTemplate.executeWithoutResult {
                    iTråd()
                    harLås.countDown()
                    slipp.await(5, TimeUnit.SECONDS)
                }
            }

        harLås.await(5, TimeUnit.SECONDS) shouldBe true
        try {
            return iMellomtiden()
        } finally {
            slipp.countDown()
            tråd.join()
        }
    }

    private fun lagreUsendtRad(
        fnr: String,
        opprettetTimestamp: Instant,
    ): OutboxDbRecord =
        outboxDbRepository.save(
            OutboxDbRecord(
                type = OutboxType.AUDIT,
                fnr = fnr,
                payload = mapOf("fnr" to fnr).tilPostgresJson(),
                opprettetTimestamp = opprettetTimestamp,
                sendtTimestamp = null,
            ),
        )
}
