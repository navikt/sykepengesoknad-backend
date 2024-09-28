package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknad
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SykepengegrunnlagForNaeringsdrivendeTest : FellesTestOppsett() {
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
        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.let { grunnlag ->
            grunnlag.fastsattSykepengegrunnlag `should be equal to` 392513.toBigInteger()
            grunnlag.gjennomsnittTotal `should be equal to` 392513.toBigInteger()
            grunnlag.grunnbeloepPerAar.size `should be equal to` 3
            grunnlag.gjennomsnittPerAar.size `should be equal to` 3
            grunnlag.endring25Prosent.let {
                it.size `should be equal to` 2
                it[0] `should be equal to` 490641.toBigInteger()
                it[1] `should be equal to` 294385.toBigInteger()
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

        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

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

        val grunnlagVerdier = sykepengegrunnlagForNaeringsdrivende.sykepengegrunnlagNaeringsdrivende(soknad)

        grunnlagVerdier `should not be` null
        grunnlagVerdier!!.toJsonNode().toString() `should be equal to`
            objectMapper.readTree(
                """
                {
                    "inntekt-2023" : 1067008,
                    "inntekt-2022" : 1129745,
                    "inntekt-2021" : 1184422,
                    "g-2021" : 104716,
                    "g-2022" : 109784,
                    "g-2023" : 116239,
                    "g-sykmelding" : 124028,
                    "beregnet-snitt" : 871798,
                    "fastsatt-sykepengegrunnlag" : 744168,
                    "beregnet-p25" : 930210,
                    "beregnet-m25" : 558126
                  }
                """.trimIndent(),
            ).toString()
    }
}