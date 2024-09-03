package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
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
            grunnlag.fastsattSykepengegrunnlag `should be equal to` 372758.toBigInteger()
            grunnlag.gjennomsnittTotal `should be equal to` 372758.toBigInteger()
            grunnlag.grunnbeloepPerAar.size `should be equal to` 3
            grunnlag.gjennomsnittPerAar.size `should be equal to` 3
            grunnlag.endring25Prosent.let {
                it.size `should be equal to` 2
                it[0] `should be equal to` 279569.toBigInteger()
                it[1] `should be equal to` 465948.toBigInteger()
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
            it.gjennomsnittTotal `should be equal to` 771107.toBigInteger()
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
        }
    }
}
