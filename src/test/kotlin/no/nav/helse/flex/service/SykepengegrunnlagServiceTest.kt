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
        grunnlagVerdier!!.let {
            it.fastsattSykepengegrunnlag `should be equal to` 372758.toBigInteger()
            it.gjennomsnittTotal `should be equal to` 372758.toBigInteger()
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
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
            it.gjennomsnittTotal `should be equal to` 771106.toBigInteger()
            it.grunnbeloepPerAar.size `should be equal to` 3
            it.gjennomsnittPerAar.size `should be equal to` 3
        }
    }
}
