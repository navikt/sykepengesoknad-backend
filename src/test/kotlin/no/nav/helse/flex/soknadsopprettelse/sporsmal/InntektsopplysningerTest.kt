package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class InntektsopplysningerTest {
    @Test
    fun `Benytt inntekter når det finnes eksakt tre år med pensjonsgivende inntekt`() {
        responseMedTreSammenhengendeInntekter().erTreAarMedSammenhengendeInntekter() shouldBeEqualTo true
    }

    @Test
    fun `Ikke benytt inntekter når det mangler pensjonsgivende inntekt for et år`() {
        responseMedManglendeInntekt().erTreAarMedSammenhengendeInntekter() shouldBeEqualTo false
    }

    private fun responseMedTreSammenhengendeInntekter(): List<HentPensjonsgivendeInntektResponse> =
        listOf(
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2023",
                pensjonsgivendeInntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 200_000,
                        ),
                    ),
            ),
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2022",
                pensjonsgivendeInntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100_000,
                        ),
                    ),
            ),
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2021",
                pensjonsgivendeInntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100_000,
                        ),
                    ),
            ),
        )

    private fun responseMedManglendeInntekt(): List<HentPensjonsgivendeInntektResponse> =
        listOf(
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2023",
                pensjonsgivendeInntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 200_000,
                        ),
                    ),
            ),
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2022",
                pensjonsgivendeInntekt = emptyList(),
            ),
            HentPensjonsgivendeInntektResponse(
                norskPersonidentifikator = "fnr-7454630",
                inntektsaar = "2021",
                pensjonsgivendeInntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100_000,
                        ),
                    ),
            ),
        )
}
