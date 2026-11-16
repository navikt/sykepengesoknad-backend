package no.nav.helse.flex.frisktilarbeid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class FriskTilArbeidCronJobTest : FakesTestOppsett() {
    @Mock
    private lateinit var leaderElection: LeaderElection

    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Mock
    private lateinit var aktiveringProducer: AktiveringProducer

    private lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @BeforeEach
    fun setUp() {
        nullstillTestdata()
        friskTilArbeidCronJob = FriskTilArbeidCronJob(leaderElection, friskTilArbeidService, aktiveringProducer)
    }

    @AfterEach
    fun nullstillTestdata() {
        friskTilArbeidRepository.deleteAll()
        sykepengesoknadRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @Test
    fun `Behandler inntil 50 vedtak når instansen er leder`() {
        val iDag = LocalDate.now()
        (1..51).forEach { dag ->
            lagreVedtak("11111111111", Periode(iDag.plusDays(dag.toLong()), iDag.plusDays(dag.toLong())))
        }
        whenever(leaderElection.isLeader()).thenReturn(true)

        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        val vedtakEtter = friskTilArbeidRepository.findAll().toList()
        vedtakEtter.count { it.behandletStatus == BehandletStatus.BEHANDLET } shouldBe 50
        vedtakEtter.count { it.behandletStatus == BehandletStatus.NY } shouldBe 1
        sykepengesoknadRepository.findAll().toList().shouldHaveSize(50).forEach {
            it.status shouldBe Soknadstatus.FREMTIDIG
        }
        SoknadKafkaProducerFake.records.shouldHaveSize(50).forEach {
            it.value().status shouldBe SoknadsstatusDTO.FREMTIDIG
        }
        verifyNoInteractions(aktiveringProducer)
    }

    @Test
    fun `Starter ikke behandling når instansen ikke er leder`() {
        val vedtak = lagreVedtak("11111111111", Periode(FriskTilArbeidTestTid.dato, FriskTilArbeidTestTid.dato.plusDays(13)))
        whenever(leaderElection.isLeader()).thenReturn(false)

        friskTilArbeidCronJob.schedulertStartBehandlingAvFriskTilArbeidVedtakStatus()

        friskTilArbeidRepository.findById(vedtak.id.shouldNotBeNull()).orElseThrow() shouldBe vedtak
        sykepengesoknadRepository.findAll().shouldBeEmpty()
        SoknadKafkaProducerFake.records.shouldBeEmpty()
        verifyNoInteractions(aktiveringProducer)
    }

    @Test
    fun `Bestiller aktivering kun for søknader som har passert tom-dato`() {
        val iDag = LocalDate.now()
        val iGar = iDag.minusDays(1)
        val iMorgen = iDag.plusDays(1)
        val utloptVedtak = lagreVedtak("11111111111", Periode(iGar.minusDays(13), iGar))
        lagreVedtak("22222222222", Periode(iMorgen, iMorgen.plusDays(13)))

        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        val argumentCaptor = argumentCaptor<AktiveringBestilling>()
        verify(aktiveringProducer, times(1)).leggPaAktiveringTopic(argumentCaptor.capture())
        val utloptSoknad = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(utloptVedtak.id.shouldNotBeNull()).single()
        argumentCaptor.firstValue shouldBe AktiveringBestilling(fnr = utloptSoknad.fnr, soknadId = utloptSoknad.sykepengesoknadUuid)
    }

    @Test
    fun `Bestiller ikke aktivering for søknader som ikke har passert tom-dato`() {
        val iDag = LocalDate.now()
        val iMorgen = iDag.plusDays(1)
        val vedtak = lagreVedtak("11111111111", Periode(iDag, iMorgen))

        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        friskTilArbeidRepository.findById(vedtak.id.shouldNotBeNull()).orElseThrow().behandletStatus shouldBe BehandletStatus.BEHANDLET
        verify(aktiveringProducer, never()).leggPaAktiveringTopic(any())
    }

    @Test
    fun `Bestiller ikke aktivering når tom er i dag`() {
        val iDag = LocalDate.now()
        val vedtak = lagreVedtak("11111111111", Periode(iDag.minusDays(13), iDag))

        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        friskTilArbeidRepository.findById(vedtak.id.shouldNotBeNull()).orElseThrow().behandletStatus shouldBe BehandletStatus.BEHANDLET
        verifyNoInteractions(aktiveringProducer)
    }

    @Test
    fun `Behandlingsfeil sendes videre uten å bestille aktivering`() {
        val behandlingsfeil = IllegalStateException("Feil ved behandling")
        val serviceSomFeiler = mock<FriskTilArbeidService>()
        whenever(serviceSomFeiler.behandleFriskTilArbeidVedtakStatus(any())).thenThrow(behandlingsfeil)
        val cronSomFeiler = FriskTilArbeidCronJob(leaderElection, serviceSomFeiler, aktiveringProducer)

        val feil = assertThrows<IllegalStateException> { cronSomFeiler.behandleFriskTilArbeidVedtak() }

        assertSame(behandlingsfeil, feil)
        verifyNoInteractions(aktiveringProducer)
    }

    private fun lagreVedtak(
        fnr: String,
        periode: Periode,
    ): FriskTilArbeidVedtakDbRecord =
        friskTilArbeidRepository.save(
            lagFriskTilArbeidVedtakDbRecord(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, periode))
                .copy(behandletStatus = BehandletStatus.NY, ignorerArbeidssokerregister = true),
        )
}
