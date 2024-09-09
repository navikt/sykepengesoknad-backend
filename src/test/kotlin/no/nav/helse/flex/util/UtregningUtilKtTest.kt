package no.nav.helse.flex.util

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

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
        ).let {
            it.first.toDouble().roundToInt().toBigInteger() `should be equal to` 589139.toBigInteger()
            it.second.toDouble().roundToInt().toBigInteger() `should be equal to` 581298.toBigInteger()
        }
    }

    @Test
    fun beregnEndring25Prosent() {
        // Heltall, ingen avrunding
        val beregnetGrunnlag1 = 500000.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag1).let {
            it[0] `should be equal to` 625000.toBigInteger() // 500000 * 1.25 = 625000
            it[1] `should be equal to` 375000.toBigInteger() // 500000 * 0.75 = 375000
        }

        // Avrunding opp og ned ved desimaler
        val beregnetGrunnlag2 = 500001.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag2).let {
            it[0] `should be equal to` 625001.toBigInteger() // 500001 * 1.25 = 625001.25, rounded to 625001
            it[1] `should be equal to` 375001.toBigInteger() // 500001 * 0.75 = 375000.75, rounded to 375001
        }

        // Avrunding opp ved 0.5
        val beregnetGrunnlag3 = 500002.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag3).let {
            it[0] `should be equal to` 625003.toBigInteger() // 500002 * 1.25 = 625002.5, rounded to 625003
            it[1] `should be equal to` 375002.toBigInteger() // 500002 * 0.75 = 375001,5, rounded to 375002
        }
    }
}
