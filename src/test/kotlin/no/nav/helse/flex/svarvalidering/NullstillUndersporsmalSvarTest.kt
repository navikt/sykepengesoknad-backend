package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.HVOR_MANGE_TIMER_PER_UKE
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_PROSENT
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_PROSENT_VERDI
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER_VERDI
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_STYREVERV
import no.nav.helse.flex.soknadsopprettelse.JOBBER_DU_NORMAL_ARBEIDSUKE
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NullstillUndersporsmalSvarTest : BaseTestClass() {
    private final val fnr = "123456789"
    private val basisdato = LocalDate.of(2023, 1, 1)

    private val soknaden get() = hentSoknad(
        soknadId = hentSoknaderMetadata(fnr).first().id,
        fnr = fnr
    )

    private val RSSporsmal.forsteSvar: String?
        get() = if (svar.isEmpty()) {
            null
        } else {
            svar[0].verdi
        }

    @BeforeAll
    fun `Bruker til slutt spørsmålet`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    @Test
    @Order(0)
    fun `Oppretter en ny arbeidstaker søknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(20)
                )
            )
        )
        val soknad = hentSoknaderMetadata(fnr).first()
        soknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        soknad.status shouldBeEqualTo RSSoknadstatus.NY
    }

    @Test
    @Order(1)
    fun `Kan svare på ANSVARSERKLARING`() {
        val svar = "CHECKED"

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, svar)

        soknaden.getSporsmalMedTag(ANSVARSERKLARING).forsteSvar shouldBeEqualTo svar
    }

    @Test
    @Order(2)
    fun `Kan svare på TILBAKE_I_ARBEID`() {
        val svar = basisdato.format(ISO_LOCAL_DATE)

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(TILBAKE_I_ARBEID, "JA", ferdigBesvart = false)
            .besvarSporsmal(TILBAKE_NAR, svar, mutert = true)

        soknaden.getSporsmalMedTag(TILBAKE_NAR).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI", mutert = true)

        soknaden.getSporsmalMedTag(TILBAKE_NAR).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(3)
    fun `Kan svare på FERIE_V2`() {
        val svar = DatoUtil.periodeTilJson(basisdato, basisdato.plusDays(2))

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FERIE_V2, "JA", ferdigBesvart = false)
            .besvarSporsmal(FERIE_NAR_V2, svar)

        soknaden.getSporsmalMedTag(FERIE_NAR_V2).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(FERIE_V2, "NEI")

        soknaden.getSporsmalMedTag(FERIE_NAR_V2).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(4)
    fun `Kan svare på PERMISJON_V2`() {
        val svar = DatoUtil.periodeTilJson(basisdato, basisdato.plusDays(2))

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(PERMISJON_V2, "JA", ferdigBesvart = false)
            .besvarSporsmal(PERMISJON_NAR_V2, svar)

        soknaden.getSporsmalMedTag(PERMISJON_NAR_V2).forsteSvar shouldBeEqualTo svar

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(PERMISJON_V2, "NEI")

        soknaden.getSporsmalMedTag(PERMISJON_NAR_V2).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(5)
    fun `Kan svare på ARBEID_UNDERVEIS_100_PROSENT`() {
        // Har ikke vanlig arbeidsuke og arbeid i prosent
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
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
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0), svar = "JA")
        soknaden.getSporsmalMedTag(medIndex(JOBBER_DU_NORMAL_ARBEIDSUKE, 0)).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MANGE_TIMER_PER_UKE, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo "79"

        // Har vanlig arbeidsuke og arbeid i timer
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(medIndex(HVOR_MYE_PROSENT, 0), svar = null, ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_TIMER, 0), svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(medIndex(HVOR_MYE_TIMER_VERDI, 0), svar = "120")
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_PROSENT_VERDI, 0)).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER, 0)).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(medIndex(HVOR_MYE_TIMER_VERDI, 0)).forsteSvar shouldBeEqualTo "120"

        // Svarer nei på hovedspørsmål
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
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
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_STYREVERV, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE, svar = "NEI")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo "NEI"

        // velger bort andre arbeidsforhold
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = null)
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo null

        // velger andre arbeidsforhold med jobbing
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE, svar = "JA")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "JA"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo "CHECKED"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo "JA"

        // Nei på hovedspørsmål nullstiller underspørsmål
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
        soknaden.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_STYREVERV).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD).forsteSvar shouldBeEqualTo null
        soknaden.getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(7)
    fun `Kan svare på UTLAND_V2`() {
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(UTLAND_V2, "NEI")

        soknaden.getSporsmalMedTag(UTLAND_V2).forsteSvar shouldBeEqualTo "NEI"
        soknaden.getSporsmalMedTag(UTLAND_NAR_V2).forsteSvar shouldBeEqualTo null
    }

    @Test
    @Order(8)
    fun `Kan svare på TIL_SLUTT`() {
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = TIL_SLUTT, svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
            .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")

        soknaden.getSporsmalMedTag(TIL_SLUTT).forsteSvar shouldBeEqualTo "Jeg lover å ikke lyve!"
        soknaden.getSporsmalMedTag(BEKREFT_OPPLYSNINGER).forsteSvar shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(9)
    fun `Kan sende inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .sendSoknad()

        soknaden.status shouldBeEqualTo RSSoknadstatus.SENDT
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
