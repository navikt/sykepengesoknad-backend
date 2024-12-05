package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClientException
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_MED_FLERE_TYPER_INNTEKT
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_MED_INNTEKT_2_AV3_AAR
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_MED_INNTEKT_OVER_1G_SISTE_3_AAR
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_MED_KUN_INNTEKT_I_AAR_4
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_SOM_IKKE_FINNES_I_SIGRUN
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_SOM_TRIGGER_RETUR_AV_TOM_BODY_FRA_SIGRUN
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_UTEN_INNTEKT_FORSTE_AAR
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.PERSON_UTEN_PENSJONSGIVENDE_INNTEKT_ALLE_AAR
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @BeforeEach
    fun nullstillSigrunMockDispatcher() {
        SigrunMockDispatcher.antallKall.set(0)
    }

    @Test
    fun `Returnerer 3 sammenhengende år med ferdiglignet inntekter`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_MED_INNTEKT_OVER_1G_SISTE_3_AAR,
                sykmeldtAar = 2024,
            )

        response?.size `should be equal to` 3
        response?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Returnerer 3 sammenhengende år for person med forskjellige type inntekt`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_MED_FLERE_TYPER_INNTEKT,
                sykmeldtAar = 2024,
            )

        response?.size `should be equal to` 3
        response?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Returnerer null når det ikke finnes ferdigligent inntekt for første tre år`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_MED_KUN_INNTEKT_I_AAR_4,
                sykmeldtAar = 2024,
            )

        response `should be` null
    }

    @Test
    fun `Henter ikke inntekt for fjerde år når to av tre første tre har ferdiglignet inntekt`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_MED_INNTEKT_2_AV3_AAR,
                sykmeldtAar = 2024,
            )

        response `should be` null
    }

    @Test
    fun `Henter inntekt for fjerde år når første år ikke er ferdiglignet`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_UTEN_INNTEKT_FORSTE_AAR,
                sykmeldtAar = 2024,
            )

        response?.size `should be equal to` 3
        response?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }

    @Test
    fun `Hopper over første år, men ekstra som hentes år er også uten inntekt`() {
        val response =
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_UTEN_PENSJONSGIVENDE_INNTEKT_ALLE_AAR,
                sykmeldtAar = 2024,
            )

        response `should be` null
    }

    @Test
    fun `Det gjøres ikke retry for PensjongivendeInntektClientException`() {
        assertThrows<PensjongivendeInntektClientException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_SOM_IKKE_FINNES_I_SIGRUN,
                sykmeldtAar = 2024,
            )
        }

        SigrunMockDispatcher.antallKall.get() `should be equal to` 1
    }

    @Test
    fun `Det gjøres retry når det kastes en annen exception enn PensjongivendeInntektClientException`() {
        assertThrows<RuntimeException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = PERSON_SOM_TRIGGER_RETUR_AV_TOM_BODY_FRA_SIGRUN,
                sykmeldtAar = 2024,
            )
        }

        // @Retryable(maxAttempts = 3)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }
}
