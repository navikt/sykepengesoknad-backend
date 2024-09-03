package no.nav.helse.flex.util

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class UtregningUtilKtTest {
    @Test
    fun sykepengegrunnlagUtregner() {
        sykepengegrunnlagUtregner(
            pensjonsgivendeInntektIKalenderAaret = 600000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 124028.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 116239.toBigInteger(),
        ) `should be equal to` 640205.toBigInteger()
    }

    @Test
    fun beregnGjennomsnittligInntekt() {
        val beregnetInntektPerAar =
            mapOf(
                "2016" to 707561.toBigInteger(),
                "2015" to 638656.toBigInteger(),
                "2014" to 543613.toBigInteger(),
            )
        beregnGjennomsnittligInntekt(
            beregnetInntektPerAar,
            grunnbeloepSykmldTidspunkt = 96883,
        ) `should be equal to` Pair(589138.toBigInteger(), 581298.toBigInteger())
    }

    @Test
    fun beregnEndring25Prosent() {
        val beregnetGrunnlag = 500000.toBigInteger()
        beregnEndring25Prosent(beregnetGrunnlag).let {
            it[0] `should be equal to` 375000.toBigInteger()
            it[1] `should be equal to` 625000.toBigInteger()
        }
    }
}
