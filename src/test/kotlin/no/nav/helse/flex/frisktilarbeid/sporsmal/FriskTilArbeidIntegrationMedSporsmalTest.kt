package no.nav.helse.flex.frisktilarbeid.sporsmal

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK
import no.nav.helse.flex.frisktilarbeid.BehandletStatus
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidService
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidTestTid
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FriskTilArbeidIntegrationMedSporsmalTest : FakesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111132"
    private val iDag = FriskTilArbeidTestTid.dato
    private lateinit var soknader: List<RSSykepengesoknad>

    @BeforeEach
    fun opprettSoknader() {
        nullstillTestdata()
        sendFriskTilArbeidVedtak(fnr, iDag.minusDays(15), iDag)
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(iDag)
        soknader = ventPaFriskTilArbeidSoknader(fnr, listOf(RSSoknadstatus.NY, RSSoknadstatus.FREMTIDIG))
    }

    @AfterEach
    fun nullstillTestdata() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @Test
    fun `Behandling aktiverer første periode med spørsmål i riktig rekkefølge`() {
        friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single().behandletStatus shouldBe BehandletStatus.BEHANDLET
        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size shouldBe 0
        soknader.map { it.status } shouldBe listOf(RSSoknadstatus.NY, RSSoknadstatus.FREMTIDIG)
        soknader.forEach { it.friskTilArbeidVedtakId shouldBe soknader.first().friskTilArbeidVedtakId }
        val soknad = soknader.first()
        soknad.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )
        soknad.getSporsmalMedTag(FTA_JOBBSITUASJONEN_DIN_JA).undersporsmal.map { it.tag } shouldBe
            listOf(FTA_JOBBSITUASJONEN_DIN_NAR, FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB)
    }

    @Test
    fun `Første søknad kan sendes med fortsatt arbeidssøker og uten inntekt`() {
        val besvarer = besvarForsteSoknad()

        val sendt = besvarer.sendSoknad()

        forventSendtSoknadUtenMutering(
            besvarer,
            sendt,
            inntektUnderveis = false,
            fortsattArbeidssoker = true,
        )
    }

    @Test
    fun `Aktivering av siste periode publiserer NY og fjerner spørsmål om fortsatt arbeidssøker`() {
        besvarForsteSoknad().sendSoknad()

        val soknad = aktiverSisteSoknad()

        val tags = listOf(soknad.getSporsmalMedTag(FTA_JOBBSITUASJONEN_DIN)).flatten().map { it.tag }
        tags shouldBe
            listOf(
                FTA_JOBBSITUASJONEN_DIN,
                FTA_JOBBSITUASJONEN_DIN_JA,
                FTA_JOBBSITUASJONEN_DIN_NAR,
                FTA_JOBBSITUASJONEN_DIN_NEI,
            )
        SoknadKafkaProducerFake.records
            .map { it.value() }
            .single { it.id == soknad.id && it.status == SoknadsstatusDTO.NY }
            .id shouldBe soknader.last().id
    }

    @Test
    fun `Siste søknad kan sendes med inntekt uten svar om fortsatt arbeidssøker`() {
        besvarForsteSoknad().sendSoknad()
        val soknad = aktiverSisteSoknad()
        val besvarer = besvarSisteSoknadMedInntekt(soknad, belop = "4000")

        val sendt = besvarer.sendSoknad()

        forventSendtSoknadUtenMutering(
            besvarer,
            sendt,
            inntektUnderveis = true,
            fortsattArbeidssoker = null,
        )
    }

    private fun forventSendtSoknadUtenMutering(
        besvarer: SoknadBesvarer,
        sendt: RSSykepengesoknad,
        inntektUnderveis: Boolean,
        fortsattArbeidssoker: Boolean?,
    ) {
        besvarer.muterteSoknaden shouldBe false
        sendt.status shouldBe RSSoknadstatus.SENDT
        val hendelse =
            SoknadKafkaProducerFake.records
                .map { it.value() }
                .single { it.id == sendt.id && it.status == SoknadsstatusDTO.SENDT }
        hendelse.inntektUnderveis shouldBe inntektUnderveis
        hendelse.fortsattArbeidssoker shouldBe fortsattArbeidssoker
    }

    private fun besvarSisteSoknadMedInntekt(
        soknad: RSSykepengesoknad,
        belop: String,
    ): SoknadBesvarer =
        SoknadBesvarer(soknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", true)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "JA", ferdigBesvart = false)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS_BELOP, belop, ferdigBesvart = true)
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()

    private fun besvarForsteSoknad(): SoknadBesvarer =
        SoknadBesvarer(hentSoknad(soknader.first().id, fnr), this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "JA", true)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()

    private fun aktiverSisteSoknad(): RSSykepengesoknad {
        aktiveringJob.bestillAktivering(iDag.plusDays(1))
        return ventPaFriskTilArbeidSoknader(fnr, listOf(RSSoknadstatus.SENDT, RSSoknadstatus.NY)).last()
    }
}
