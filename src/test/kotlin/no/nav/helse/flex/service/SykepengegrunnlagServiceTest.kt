package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.inntektskomponenten.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.inntektskomponenten.PensjonsgivendeInntekt
import no.nav.helse.flex.client.inntektskomponenten.Skatteordning
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SykepengegrunnlagServiceTest : FellesTestOppsett() {
    @Test
    fun `sjekker utregning av sykepengegrunnlag`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val grunnlagVerdier = sykepengegrunnlagService.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.let { grunnlag ->
            grunnlag.fastsattSykepengegrunnlag `should be equal to` 392513.toBigInteger()
            grunnlag.gjennomsnittTotal `should be equal to` 392513.toBigInteger()
            grunnlag.grunnbeloepPerAar.size `should be equal to` 3
            grunnlag.gjennomsnittPerAar.size `should be equal to` 3
            grunnlag.endring25Prosent.let {
                it.size `should be equal to` 2
                it[0] `should be equal to` 294385.toBigInteger()
                it[1] `should be equal to` 490641.toBigInteger()
            }
        }
    }

    @Test
    fun `sjekker utregning for inntekt mellom 6G og 12G`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "87654321234",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )

        val grunnlagVerdier = sykepengegrunnlagService.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.let {
            it.fastsattSykepengegrunnlag `should be equal to` 744168.toBigInteger()
            it.gjennomsnittTotal `should be equal to` 871798.toBigInteger()
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
        }
    }

    @Test
    fun `sjekker json for frontend`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "87654321234",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )

        val grunnlagVerdier = sykepengegrunnlagService.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.toJsonNode().toString() `should be equal to`
            objectMapper.readTree(
                """
                {
                  "inntekt" : {
                    "inntekt-2023" : 1067008,
                    "inntekt-2022" : 1129745,
                    "inntekt-2021" : 1184422,
                    "g-2021" : 104716,
                    "g-2022" : 109784,
                    "g-2023" : 116239,
                    "g-sykmelding" : 124028,
                    "beregnet-snitt" : 871798,
                    "fastsatt-sykepengegrunnlag" : 744168,
                    "beregnet-p25" : 558126,
                    "beregnet-m25" : 930210
                  }
                }
                """.trimIndent(),
            ).toString()
    }

    @Test
    fun `skal returnere 3 år med gyldige inntekter`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "87654321234",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "87654321234",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "87654321234",
                    "2022",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "87654321234",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
            )
    } // personMedInntektOver1GSiste3Aar

    @Test
    fun `skal returnere 3 år med gyldige inntekter under 1G`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "24859597781",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "24859597781",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "24859597781",
                    "2022",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "24859597781",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
            )
    } // personMedInntektUnder1GSiste3Aar

    @Test
    fun `skal returnere null når ingen inntekter finnes for tre første år`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "56830375185",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result `should be equal to` null
    } // personUtenInntektSiste3Aar

    @Test
    fun `skal returnere null når første år har inntekt og de to neste har ikke`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "12899497862",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result `should be equal to` null
    } // personMedInntekt1Av3Aar

    @Test
    fun `skal hente et fjerde år når første og tredje år har inntekt, men ikke andre`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "56909901141",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "56909901141",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "56909901141",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "56909901141",
                    "2020",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
            )
    } // personMedInntekt2Av3Aar

    @Test
    fun `skal returnere 3 år når det er 1 år over 1G og 2 år under 1G`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "07830099810",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "07830099810",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "07830099810",
                    "2022",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "07830099810",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
            )
    } // personMedInntektOver1G1Av3Aar

    @Test
    fun `skal returnere 3 år når det er 2 år over 1G og 1 år under 1G`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "11929798688",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result = sykepengegrunnlagService.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, soknad.startSykeforlop!!.year)

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "11929798688",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "11929798688",
                    "2022",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 100000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "11929798688",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 1000000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
            )
    } // personMedInntektOver1G1Av3Aar
}
