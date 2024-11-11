package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @Test
    fun `Returnere 3 sammenhengende år med ferdiglignet inntekter`() {
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

        result?.size `should be equal to` 3
        result?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    } // personMedInntektOver1GSiste3Aar

    @Test
    fun `Returnerer null når 2 første år ikke ikke finnes`() {
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

        result `should be` null
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

        result `should be` null
    }

    @Test
    fun `Returnerer null det ikke finnes ferdiglignet inntekt 3 sammenhengende år`() {
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

        result `should be` null
    }

    @Test
    fun `Hopper over første år som ikke er ferdiglignet`() {
        val soknad =
            opprettNyNaeringsdrivendeSoknad().copy(
                fnr = "21127575934",
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
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }
}
