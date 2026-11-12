package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.frisktilarbeid.sporsmal.ventPaFriskTilArbeidSoknader
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.util.osloZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EksternStoppmeldingTest : FakesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111122"
    private val iDag = FriskTilArbeidTestTid.dato
    private val avsluttetTidspunkt = iDag.atStartOfDay(osloZone).toInstant()
    private val vedtakFom = iDag.minusDays(15)
    private val vedtakTom = iDag.plusDays(35)
    private lateinit var vedtakId: String

    @BeforeEach
    fun opprettSoknader() {
        nullstillTestdata()
        sendFriskTilArbeidVedtak(fnr, vedtakFom, vedtakTom)
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(iDag)
        ventPaFriskTilArbeidSoknader(
            fnr,
            listOf(RSSoknadstatus.NY, RSSoknadstatus.FREMTIDIG, RSSoknadstatus.FREMTIDIG, RSSoknadstatus.FREMTIDIG),
        )
        val vedtak = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single()
        check(vedtak.behandletStatus == BehandletStatus.BEHANDLET)
        vedtakId = vedtak.id.shouldNotBeNull()
    }

    @AfterEach
    fun nullstillTestdata() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @Test
    fun `Stopp sletter fremtidige perioder og publiserer slettingene`() {
        sendStoppMelding(vedtakId, fnr, avsluttetTidspunkt)

        forventSlettedeSoknaderForVedtak(
            vedtakId,
            forventedeStartdatoer = listOf(vedtakFom.plusDays(28), vedtakFom.plusDays(42)),
        )

        hentSoknader(fnr).sortedBy { it.fom }.map { it.fom to it.status } shouldBe
            listOf(vedtakFom to RSSoknadstatus.NY, vedtakFom.plusDays(14) to RSSoknadstatus.FREMTIDIG)
        friskTilArbeidRepository.findById(vedtakId).get().avsluttetTidspunkt shouldBe avsluttetTidspunkt
    }

    @Test
    fun `Samme stoppmelding behandlet to ganger gir ingen nye slettinger eller hendelser`() {
        sendStoppMelding(vedtakId, fnr, avsluttetTidspunkt)
        val vedtakEtterForsteStopp = friskTilArbeidRepository.findById(vedtakId).get()
        val soknaderEtterForsteStopp = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(vedtakId).sortedBy { it.fom }
        val hendelserEtterForsteStopp = SoknadKafkaProducerFake.records.toList()
        check(soknaderEtterForsteStopp.size == 2)
        check(hendelserEtterForsteStopp.count { it.value().status == SoknadsstatusDTO.SLETTET } == 2)

        sendStoppMelding(vedtakId, fnr, avsluttetTidspunkt)

        friskTilArbeidRepository.findById(vedtakId).get() shouldBe vedtakEtterForsteStopp
        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(vedtakId).sortedBy { it.fom } shouldBe soknaderEtterForsteStopp
        SoknadKafkaProducerFake.records.toList() shouldBe hendelserEtterForsteStopp
    }

    @Test
    fun `Nytt vedtak kan starte dagen etter stopp og opprette fremtidige søknader`() {
        sendStoppMelding(vedtakId, fnr, avsluttetTidspunkt)
        check(friskTilArbeidRepository.findById(vedtakId).get().avsluttetTidspunkt == avsluttetTidspunkt)
        sendFriskTilArbeidVedtak(fnr, iDag.plusDays(1), vedtakTom)
        val nyttVedtak = friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).single()
        val nyttVedtakId = nyttVedtak.id.shouldNotBeNull()

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).shouldBeEmpty()
        friskTilArbeidRepository.findById(nyttVedtakId).get().behandletStatus shouldBe BehandletStatus.BEHANDLET
        val soknader = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(nyttVedtakId).sortedBy { it.fom }
        soknader.map { it.fom to it.tom } shouldBe
            listOf(
                iDag.plusDays(1) to iDag.plusDays(14),
                iDag.plusDays(15) to iDag.plusDays(28),
                iDag.plusDays(29) to vedtakTom,
            )
        soknader.forEach { it.status shouldBe Soknadstatus.FREMTIDIG }
        SoknadKafkaProducerFake.records
            .map { it.value() }
            .filter { it.friskTilArbeidVedtakId == nyttVedtak.id }
            .map { it.status } shouldBe List(3) { SoknadsstatusDTO.FREMTIDIG }
    }
}
