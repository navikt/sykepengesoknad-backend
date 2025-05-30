package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NullstillUndersporsmalSvarTest : FellesTestOppsett() {
    private final val fnr = "123456789"
    private val basisdato = LocalDate.of(2023, 1, 1)

    private val soknaden get() =
        hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr,
        )

    private val RSSporsmal.forsteSvar: String?
        get() =
            if (svar.isEmpty()) {
                null
            } else {
                svar[0].verdi
            }

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(0)
    fun `Oppretter en ny arbeidstaker søknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato,
                        tom = basisdato.plusDays(20),
                    ),
            ),
        )
        val soknad = hentSoknaderMetadata(fnr).first()
        soknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        soknad.status shouldBeEqualTo RSSoknadstatus.NY
    }

    @Test
    @Order(1)
    fun `Kan svare på ANSVARSERKLARING`() {
        val svar = "CHECKED"

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, svar)

        soknaden.getSporsmalMedTag(ANSVARSERKLARING).forsteSvar shouldBeEqualTo svar
    }

    @Test
    @Order(2)
    fun `Kan svare på TILBAKE_I_ARBEID`() {
        val svar = basisdato.format(ISO_LOCAL_DATE)

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(TILBAKE_I_ARBEID, "JA", ferdigBesvart = false)
            .besvarSporsmal(TILBAKE_NAR, svar, mutert = true)

        soknaden.getSporsmalMedTag(TILBAKE_NAR).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI", mutert = true)

        soknaden.getSporsmalMedTag(TILBAKE_NAR).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(3)
    fun `Kan svare på FERIE_V2`() {
        val svar = DatoUtil.periodeTilJson(basisdato, basisdato.plusDays(2))

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FERIE_V2, "JA", ferdigBesvart = false)
            .besvarSporsmal(FERIE_NAR_V2, svar)

        soknaden.getSporsmalMedTag(FERIE_NAR_V2).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FERIE_V2, "NEI")

        soknaden.getSporsmalMedTag(FERIE_NAR_V2).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(4)
    fun `Kan svare på PERMISJON_V2`() {
        val svar = DatoUtil.periodeTilJson(basisdato, basisdato.plusDays(2))

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(PERMISJON_V2, "JA", ferdigBesvart = false)
            .besvarSporsmal(PERMISJON_NAR_V2, svar)

        soknaden.getSporsmalMedTag(PERMISJON_NAR_V2).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(PERMISJON_V2, "NEI")

        soknaden.getSporsmalMedTag(PERMISJON_NAR_V2).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(5)
    fun `Kan svare på ARBEID_UNDERVEIS_100_PROSENT`() {
        // Har ikke vanlig arbeidsuke og arbeid i prosent
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), "JA", ferdigBesvart = false)
            .besvarSporsmal(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0), "NEI", ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MANGE_TIMER_PER_UKE, 0), "37,5", ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_PROSENT, 0), "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_PROSENT_VERDI, 0), "79")
        soknaden.getSporsmalMedTag(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0)).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MANGE_TIMER_PER_UKE, 0)).forsteSvar shouldBeEqualTo "37,5"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo "79"

        // Har vanlig arbeidsuke og arbeid i prosent
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0), svar = "JA")
        soknaden.getSporsmalMedTag(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0)).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MANGE_TIMER_PER_UKE, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo "79"

        // Har vanlig arbeidsuke og arbeid i timer
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(medIndex(HVOR_MYE_PROSENT, 0), svar = null, ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_TIMER, 0), svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_TIMER_VERDI, 0), svar = "120")
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER, 0)).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER_VERDI, 0)).forsteSvar shouldBeEqualTo "120"

        // Svarer nei på hovedspørsmål
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
        soknaden.getSporsmalMedTag(medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0)).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MANGE_TIMER_PER_UKE, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER_VERDI, 0)).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(6)
    fun `Kan svare på ANDRE_INNTEKTSKILDER_V2`() {
        // styreverv og andre arbeidsforhold uten jobbing
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_STYREVERV, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE, svar = "NEI")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo "NEI"

        // velger bort andre arbeidsforhold
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = null)
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo null

        // velger andre arbeidsforhold med jobbing
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE, svar = "JA")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo "JA"

        // Nei på hovedspørsmål nullstiller underspørsmål
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(7)
    fun `Kan svare på OPPHOLD_UTENFOR_EOS`() {
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")

        soknaden.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS_NAR).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(8)
    fun `Kan svare på TIL_SLUTT`() {
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .oppsummering()

        soknaden.getSporsmalMedTag(TIL_SLUTT).forsteSvar shouldBeEqualTo "true"
    }

    @Test
    @Order(9)
    fun `Kan sende inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .sendSoknad()

        soknaden.status shouldBeEqualTo RSSoknadstatus.SENDT
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }
}
