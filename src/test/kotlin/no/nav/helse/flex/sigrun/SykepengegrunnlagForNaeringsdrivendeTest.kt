package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.service.finnFoersteAarISykepengegrunnlaget
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

private const val FNR = "12345678910"

class SykepengegrunnlagForNaeringsdrivendeTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        with(SigrunMockDispatcher) {
            antallKall.set(0)
            clearQueue()
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt under 6G`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 400_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-19",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 50_000,
                        ),
                    ),
            )
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` true
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2021
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt mellom 6G og 12G`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                        ),
                    ),
            )
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2021
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt over 12G`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 2_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 2_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 2_000_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2021
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt under og over 6G og over 12G`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 2_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1_000_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2021
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag med forskjellige opptjeningssted og inntektstype`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 150_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 250_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 1_250_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 200_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2021
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag når fjerde år hentes siden første år mangler inntekt`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 5

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2020
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag når fjerde år hentes siden første år mangler inntekt og det mangler inntekt ett år`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 5

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.inntekter.size `should be equal to` 3
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2020
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag selv om det mangler inntekt to år`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
            enqueueResponse(sigrun404Feil())
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag =
            sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(
                lagSykepengesoknad(dato = LocalDate.of(2023, 10, 1)),
            )
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.inntekter.size `should be equal to` 3
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2020
        }
    }

    @Test
    fun `Det blir beregnet sykepengegrunnlag selv om alle år mangler inntekter`() {
        with(SigrunMockDispatcher) {
            repeat(4) {
                enqueueResponse(sigrun404Feil())
            }
        }
        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 5

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` true
        sykepengegrunnlag.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2020
    }

    @Test
    fun `Bruker mangler inntekt året før sykepengegrunnlaget`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2024",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2024-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvNaeringsinntekt = 250_000,
                        ),
                    ),
            )
            enqueueResponse(sigrun404Feil())
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad(LocalDate.of(2025, 3, 1)))
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let { spg ->
            spg.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
            spg.inntekter.finnFoersteAarISykepengegrunnlaget() `should be equal to` 2022
        }
    }

    private fun lagSykepengesoknad(dato: LocalDate = LocalDate.of(2024, 10, 1)): Sykepengesoknad =
        opprettNyNaeringsdrivendeSoknad().copy(
            fnr = FNR,
            startSykeforlop = dato,
            fom = dato.minusDays(30),
            tom = dato.minusDays(1),
            sykmeldingSkrevet = dato.atTime(0, 0).minusDays(1).toInstant(ZoneOffset.UTC),
            aktivertDato = dato.minusDays(30),
        )
}
