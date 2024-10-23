package no.nav.helse.flex.util

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.math.roundToInt

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtregningUtilKtTest {
    @Disabled
    @Test
    fun `inntektJustertForGrunnbeloep runder riktig ned`() {
        // 640205,09
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 600000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 124028.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 116239.toBigInteger(),
        ) `should be equal to` 640205.toBigInteger()
    }

    @Disabled
    @Test
    fun `inntektJustertForGrunnbeloep runder riktig opp`() {
        // 296105,65
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 250000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 124028.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 104716.toBigInteger(),
        ) `should be equal to` 296106.toBigInteger()
    }

    @Test
    @Order(1)
    fun `inntektJustertForGrunnbeloep Excel-scenario 1 2022`() {
        // 2024
        val gPaaSykmeldingstidspunktet = 124_028.toBigInteger()

        // 2022: 112974,57
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 100_000.toBigInteger(),
            gPaaSykmeldingstidspunktet = gPaaSykmeldingstidspunktet,
            gjennomsnittligGIKalenderaaret = 109_784.toBigInteger(),
        ) `should be equal to` 112975.toBigInteger()
    }

    @Test
    @Order(2)
    fun `inntektJustertForGrunnbeloep Excel-scenario 1 2021`() {
        // 2024
        val gPaaSykmeldingstidspunktet = 124_028.toBigInteger()

        // 2021: 1184422,63
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 1000_000.toBigInteger(),
            gPaaSykmeldingstidspunktet = gPaaSykmeldingstidspunktet,
            gjennomsnittligGIKalenderaaret = 104_716.toBigInteger(),
        ) `should be equal to` 1184423.toBigInteger()
    }

    @Test
    @Order(3)
    fun `inntektJustertForGrunnbeloep Excel-scenario 1 2020`() {
        // 2024
        val gPaaSykmeldingstidspunktet = 124_028.toBigInteger()

        // 2020: 122978,99
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 100_000.toBigInteger(),
            gPaaSykmeldingstidspunktet = gPaaSykmeldingstidspunktet,
            gjennomsnittligGIKalenderaaret = 100_853.toBigInteger(),
        ) `should be equal to` 122979.toBigInteger()
    }

    @Test
    @Order(4)
    fun `inntektJustertForGrunnbeloep Excel-scenario 2 2016`() {
        // 2018
        val gPaaSykmeldingstidspunktet = 96_883.toBigInteger()

        // 2016: 707560,61
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 670000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 96883.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 91740.toBigInteger(),
        ) `should be equal to` 707561.toBigInteger()
    }

    @Test
    @Order(5)
    fun `inntektJustertForGrunnbeloep Excel-scenario 2 2015`() {
        // 2018
        val gPaaSykmeldingstidspunktet = 96_883.toBigInteger()

        // 2015: 638655,78
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 590000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 96883.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 89502.toBigInteger(),
        ) `should be equal to` 638656.toBigInteger()
    }

    @Test
    @Order(6)
    fun `inntektJustertForGrunnbeloep Excel-scenario 2 2014`() {
        // 2018
        val gPaaSykmeldingstidspunktet = 96_883.toBigInteger()

        // 2014: 543613,39
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 490000.toBigInteger(),
            gPaaSykmeldingstidspunktet = gPaaSykmeldingstidspunktet,
            gjennomsnittligGIKalenderaaret = 87328.toBigInteger(),
        ) `should be equal to` 543613.toBigInteger()
    }

    @Disabled
    @Test
    fun justerFor6Gog12G() {
        finnInntekterJustertFor6Gog12G(
            mapOf(
                "2016" to 707561.toBigInteger(),
                "2015" to 638656.toBigInteger(),
                "2014" to 543613.toBigInteger(),
            ),
            grunnbeloepSykmldTidspunkt = 96883,
        ) `should be equal to`
            mapOf(
                "2016" to "623385.67".toBigDecimal(),
                "2015" to "600417.33".toBigDecimal(),
                "2014" to "543613.00".toBigDecimal(),
            )
    }

    @Disabled
    @Test
    fun beregnGjennomsnittligInntekt() {
        val justerteInntekter =
            mapOf(
                "2016" to "623385.67".toBigDecimal(),
                "2015" to "600417.33".toBigDecimal(),
                "2014" to "543613.00".toBigDecimal(),
            )
        beregnGjennomsnittligInntekt(justerteInntekter).toDouble().roundToInt()
            .toBigInteger() `should be equal to` 589139.toBigInteger()
    }

    @Disabled
    @Test
    fun beregnEndring25Prosent() {
        // Heltall, ingen avrunding
        val beregnetGrunnlag1 = 500000.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag1).let {
            it.p25 `should be equal to` 625000.toBigInteger() // 500000 * 1.25 = 625000
            it.m25 `should be equal to` 375000.toBigInteger() // 500000 * 0.75 = 375000
        }

        // Avrunding opp og ned ved desimaler
        val beregnetGrunnlag2 = 500001.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag2).let {
            it.p25 `should be equal to` 625001.toBigInteger() // 500001 * 1.25 = 625001.25, rounded to 625001
            it.m25 `should be equal to` 375001.toBigInteger() // 500001 * 0.75 = 375000.75, rounded to 375001
        }

        // Avrunding opp ved 0.5
        val beregnetGrunnlag3 = 500002.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag3).let {
            it.p25 `should be equal to` 625003.toBigInteger() // 500002 * 1.25 = 625002.5, rounded to 625003
            it.m25 `should be equal to` 375002.toBigInteger() // 500002 * 0.75 = 375001,5, rounded to 375002
        }
    }
}
