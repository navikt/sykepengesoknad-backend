package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.enqueueMockResponse
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.lag404MockResponse
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be`
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
        SigrunMockDispatcher.antallKall.set(0)
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt under 6G`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 392514.toBigInteger()
                it.p25 `should be equal to` 490642.toBigInteger()
                it.m25 `should be equal to` 294385.toBigInteger()
            }
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt mellom 6G og 12G`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 871798.toBigInteger()
                it.p25 `should be equal to` 1089748.toBigInteger()
                it.m25 `should be equal to` 653849.toBigInteger()
            }
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt over 12G`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 992224.toBigInteger()
                it.p25 `should be equal to` 1240280.toBigInteger()
                it.m25 `should be equal to` 744168.toBigInteger()
            }
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag for tre år med inntekt under og over 6G og over 12G`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 720341.toBigInteger()
                it.p25 `should be equal to` 900426.toBigInteger()
                it.m25 `should be equal to` 540256.toBigInteger()
            }
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag med forskjellige opptjeningssted og inntektstype`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 518361.toBigInteger()
                it.p25 `should be equal to` 647951.toBigInteger()
                it.m25 `should be equal to` 388770.toBigInteger()
            }
        }
    }

    @Test
    fun `Beregner sykepengegrunnlag når fjerde år hentes siden første år mangler inntekt`() {
        with(sigrunMockWebServer) {
            enqueue(lag404MockResponse())
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 295330.toBigInteger()
                it.p25 `should be equal to` 369162.toBigInteger()
                it.m25 `should be equal to` 221497.toBigInteger()
            }
        }
    }

    @Test
    fun `Det blir ikke beregnet sykepengegrunnlag når det returneres null fordi det mangler år`() {
        with(sigrunMockWebServer) {
            repeat(3) {
                enqueue(lag404MockResponse())
            }
        }
        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        sykepengegrunnlag `should be` null
    }

    @Test
    fun `Bruker grunnbeløp fra 2024 når ggrunnbeløp for 2025 ikke finnes enda`() {
        with(sigrunMockWebServer) {
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
        }

        val sykepengegrunnlag = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad(LocalDate.of(2025, 3, 1)))
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        sykepengegrunnlag `should not be` null
        sykepengegrunnlag!!.let {
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
            it.beregnetSnittOgEndring25.let {
                it.snitt `should be equal to` 267625.toBigInteger()
                it.p25 `should be equal to` 334532.toBigInteger()
                it.m25 `should be equal to` 200719.toBigInteger()
            }
        }
    }

    @Test
    fun `Verifiser at JSON sendt til frontend har riktige heltallsverdier`() {
        with(sigrunMockWebServer) {
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
        }

        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.beregnSykepengegrunnlag(lagSykepengesoknad())
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.toJsonNode().toString() `should be equal to`
            objectMapper
                .readTree(
                    """  
                    {"sigrunInntekt":{"inntekter":[{"aar":"2023","verdi":851782},{"aar":"2022","verdi":872694},{"aar":"2021","verdi":890920}],"g-verdier":[{"aar":"2021","verdi":104716},{"aar":"2022","verdi":109784},{"aar":"2023","verdi":116239}],"g-sykmelding":124028,"beregnet":{"snitt":871798,"p25":1089748,"m25":653849},"original-inntekt":[{"inntektsaar":"2023","pensjonsgivendeInntekt":[{"datoForFastsetting":"2023-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2022","pensjonsgivendeInntekt":[{"datoForFastsetting":"2022-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000},{"inntektsaar":"2021","pensjonsgivendeInntekt":[{"datoForFastsetting":"2021-07-17","skatteordning":"FASTLAND","loenn":0,"loenn-bare-pensjon":0,"naering":1000000,"fiske-fangst-familiebarnehage":0}],"totalInntekt":1000000}]}}
                    """.trimIndent(),
                ).toString()
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
