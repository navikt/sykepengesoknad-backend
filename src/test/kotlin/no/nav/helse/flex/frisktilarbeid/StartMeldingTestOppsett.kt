package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.frisktilarbeid.sporsmal.ventPaFriskTilArbeidSoknader
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sendStartMelding
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.FTA_INNTEKT_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_NEI
import no.nav.helse.flex.soknadsopprettelse.FTA_REISE_TIL_UTLANDET
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.osloZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

abstract class StartMeldingTestOppsett : FakesTestOppsett() {
    @Autowired
    protected lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    protected lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    protected val fnr = "11111111122"
    protected val tidspunkt = FriskTilArbeidTestTid.dato
    protected val vedtakFom get() = tidspunkt.minusDays(15)
    protected val vedtakTom get() = tidspunkt.plusDays(35)
    protected val forsteSoknadFom get() = vedtakFom
    protected val andreSoknadFom get() = vedtakFom.plusDays(14)
    protected val tredjeSoknadFom get() = vedtakFom.plusDays(28)
    protected val fjerdeSoknadFom get() = vedtakFom.plusDays(42)
    protected val stoppTidspunkt get() = tidspunkt.tilOsloInstant()
    protected val startTidspunkt get() = tidspunkt.plusDays(1).tilOsloInstant()

    @BeforeEach
    fun klargjorStartMeldingTest() {
        ryddTilstand()
    }

    @AfterEach
    fun ryddEtterStartMeldingTest() {
        ryddTilstand()
    }

    protected fun hentVedtak(): FriskTilArbeidVedtakDbRecord = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single()

    protected fun hentSoknader(): List<SykepengesoknadDbRecord> =
        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(hentVedtak().id!!).sortedBy { it.fom }

    protected fun hentHendelser(): List<SykepengesoknadDTO> = SoknadKafkaProducerFake.records.map { it.value() }

    protected data class Vedtakstilstand(
        val vedtak: FriskTilArbeidVedtakDbRecord,
        val soknader: List<SykepengesoknadDbRecord>,
        val hendelser: List<SykepengesoknadDTO>,
    )

    protected fun hentSamletVedtakstilstand(): Vedtakstilstand =
        Vedtakstilstand(
            vedtak = hentVedtak(),
            soknader = hentSoknader(),
            hendelser = hentHendelser(),
        )

    protected fun forventUendretVedtakSoknaderOgHendelser(forventet: Vedtakstilstand) {
        hentVedtak() shouldBe forventet.vedtak
        hentSoknader() shouldContainExactlyInAnyOrder forventet.soknader
        hentHendelser() shouldBe forventet.hendelser
    }

    protected fun gittAtEtVedtakErMottattOgSoknaderOpprettet(
        fom: LocalDate = vedtakFom,
        tom: LocalDate = vedtakTom,
    ) {
        sendFriskTilArbeidVedtak(fnr, fom, tom)
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(tidspunkt)
        ventPaFriskTilArbeidSoknader(fnr, soknadstatuserEtterOpprettelse)
        hentVedtak().behandletStatus shouldBe BEHANDLET
    }

    protected fun soknaderMedPassertTomErAktivert() {
        hentSoknader()
            .filter { it.tom!! < tidspunkt }
            .all { it.status == no.nav.helse.flex.domain.Soknadstatus.NY } shouldBe true
    }

    protected fun stoppMeldingHarSlettetSoknader(tidspunkt: Instant): List<String> {
        val soknaderFor = hentSoknader().map { it.sykepengesoknadUuid }

        sendStoppMelding(hentVedtak().id!!, fnr, tidspunkt)

        val slettedeSoknadIder = soknaderFor - hentSoknader().map { it.sykepengesoknadUuid }.toSet()
        check(slettedeSoknadIder.isNotEmpty())
        return slettedeSoknadIder
    }

    protected fun vedtakErAvsluttetMenIkkeBehandlet() {
        friskTilArbeidRepository.save(
            hentVedtak().copy(avsluttetTidspunkt = stoppTidspunkt, behandletStatus = BehandletStatus.NY),
        )
    }

    protected fun vedtakBlirManueltRebehandlet() {
        friskTilArbeidRepository.save(hentVedtak().copy(behandletStatus = BehandletStatus.NY))
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(tidspunkt)
        ventPaFriskTilArbeidSoknader(fnr, soknadstatuserEtterInnsendtSvar)
    }

    protected fun vedtakIgnorererArbeidssokerregisteret() {
        friskTilArbeidRepository.save(hentVedtak().copy(ignorerArbeidssokerregister = true))
    }

    protected fun brukerSvarerAtDeIkkeFortsattErFriskmeldt(soknad: RSSykepengesoknad) {
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR,
                soknad.fom!!.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                true,
                mutert = true,
            ).besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .sendSoknad()
    }

    protected fun mottaOgProsesserStartMelding(forventedeStatuser: List<RSSoknadstatus> = soknadstatuserEtterOpprettelse) {
        sendStartMelding(hentVedtak().id!!, fnr, startTidspunkt)
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(tidspunkt)
        ventPaFriskTilArbeidSoknader(fnr, forventedeStatuser)
    }

    protected fun publisertFremtidigEventFor(soknadIder: List<String>) {
        hentHendelser()
            .filter { it.status == SoknadsstatusDTO.FREMTIDIG }
            .map { it.id }
            .shouldContainAll(soknadIder)
    }

    protected fun hentNySoknad(): RSSykepengesoknad = hentSoknader(fnr).single { it.status == RSSoknadstatus.NY }

    private fun ryddTilstand() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    private val soknadstatuserEtterOpprettelse =
        listOf(
            RSSoknadstatus.NY,
            RSSoknadstatus.FREMTIDIG,
            RSSoknadstatus.FREMTIDIG,
            RSSoknadstatus.FREMTIDIG,
        )

    private val soknadstatuserEtterInnsendtSvar =
        listOf(
            RSSoknadstatus.SENDT,
            RSSoknadstatus.FREMTIDIG,
            RSSoknadstatus.FREMTIDIG,
            RSSoknadstatus.FREMTIDIG,
        )

    private fun LocalDate.tilOsloInstant(): Instant = atStartOfDay(osloZone).toInstant()
}
