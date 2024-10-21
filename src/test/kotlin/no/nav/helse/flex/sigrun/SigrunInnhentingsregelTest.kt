package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SigrunInnhentingsregelTest : FellesTestOppsett() {
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
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

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
    fun `skal returnere 3 år med gyldige inntekter fra alle inntektskilder`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "86543214356",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

        result!!.size `should be equal to` 3
        result `should be equal to`
            listOf(
                HentPensjonsgivendeInntektResponse(
                    "86543214356",
                    "2023",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 1000000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
                        ),
                    ),
                ),
                HentPensjonsgivendeInntektResponse(
                    "86543214356",
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
                    "86543214356",
                    "2021",
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 1000000,
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
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

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
    fun `skal returnere respons som har nullverdier per år når ingen inntekter finnes for tre første år`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "56830375185",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

        result?.size `should be equal to` 3
        result?.all { it.pensjonsgivendeInntekt.isEmpty() }
    } // personUtenInntektSiste3Aar

    @Test
    fun `skal hoppe over forrige år når det ikke er ferdiglignet, og returnere 2 år med null og 1 med verdier`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "12899497862",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

        result?.size `should be equal to` 3
        result?.let {
            it[0].pensjonsgivendeInntekt `should be` emptyList()
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt `should be` emptyList()
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt `should not be` emptyList<PensjonsgivendeInntekt>()
            it[2].inntektsaar `should be equal to` "2020"
        }
    } // personMedInntektAar4

    @Test
    fun `skal ikke hente et fjerde år når 1 av 3 år ikke returnerer inntekt`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "56909901141",
                startSykeforlop = LocalDate.now(),
                fom = LocalDate.now().minusDays(30),
                tom = LocalDate.now().minusDays(1),
                sykmeldingSkrevet = Instant.now(),
                aktivertDato = LocalDate.now().minusDays(30),
            )
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

        result?.size `should be equal to` 3
        result?.let {
            it[0].pensjonsgivendeInntekt `should not be` emptyList<PensjonsgivendeInntekt>()
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt `should be` emptyList()
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should not be` emptyList<PensjonsgivendeInntekt>()
            it[2].inntektsaar `should be equal to` "2021"
        }
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
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

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
        val result =
            sykepengegrunnlagForNaeringsdrivende.hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                soknad.startSykeforlop!!.year,
            )

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
