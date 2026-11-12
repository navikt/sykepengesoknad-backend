package no.nav.helse.flex.frisktilarbeid.sporsmal

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidService
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidTestTid
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class FriskTilArbeidSporsmalMuteringTest : FakesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111177"
    private lateinit var soknad: RSSykepengesoknad

    @BeforeEach
    fun opprettSoknader() {
        nullstillTestdata()
        sendFriskTilArbeidVedtak(fnr, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 28))
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        aktiveringJob.bestillAktivering(FriskTilArbeidTestTid.dato)
        soknad = ventPaFriskTilArbeidSoknader(fnr, listOf(RSSoknadstatus.NY, RSSoknadstatus.NY)).first()
    }

    @AfterEach
    fun nullstillTestdata() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @Test
    fun `Jobb fra første dag fjerner spørsmål om inntekt og utenlandsreise`() {
        val besvarer = svarJobbFraForsteDag()

        besvarer.muterteSoknaden shouldBe true
        hentSoknad(soknad.id, fnr).sporsmal!!.map { it.tag } shouldBe
            listOf(ANSVARSERKLARING, FTA_JOBBSITUASJONEN_DIN, TIL_SLUTT)
    }

    @Test
    fun `Endret svar gjenoppretter spørsmål med avkortet periode`() {
        check(svarJobbFraForsteDag().muterteSoknaden)

        val besvarer = svarAvregistrertAndreDag()

        besvarer.muterteSoknaden shouldBe true
        val mutertSoknad = hentSoknad(soknad.id, fnr)
        mutertSoknad.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )
        mutertSoknad.getSporsmalMedTag(FTA_INNTEKT_UNDERVEIS).sporsmalstekst shouldBe
            "Hadde du  inntekt i perioden 1. - 1. januar 2020?"
        mutertSoknad.getSporsmalMedTag(FTA_REISE_TIL_UTLANDET).sporsmalstekst shouldBe
            "Var du på reise utenfor EU/EØS i perioden 1. - 1. januar 2020?"
    }

    @Test
    fun `Svar på inntekt muterer ikke søknaden etter gjenoppretting av spørsmål`() {
        check(svarJobbFraForsteDag().muterteSoknaden)
        check(svarAvregistrertAndreDag().muterteSoknaden)
        val forSvar = hentSoknad(soknad.id, fnr)

        val besvarer =
            SoknadBesvarer(forSvar, this, fnr)
                .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")

        besvarer.muterteSoknaden shouldBe false
        hentSoknad(soknad.id, fnr).sporsmal!!.map { it.tag } shouldBe forSvar.sporsmal!!.map { it.tag }
    }

    private fun svarJobbFraForsteDag(): SoknadBesvarer =
        SoknadBesvarer(hentSoknad(soknad.id, fnr), this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, "2020-01-01", true, mutert = true)

    private fun svarAvregistrertAndreDag(): SoknadBesvarer =
        SoknadBesvarer(hentSoknad(soknad.id, fnr), this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_JA, null, false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR, "2020-01-02", true, mutert = true)
}
