package no.nav.helse.flex.util

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class UtregningUtilKtTest {
    @Test
    fun `inntektJustertForGrunnbeloep runder riktig ned til to desimaler`() {
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 600_000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 124_028.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 116_239.toBigInteger(),
        ) `should be equal to` 640_205.09.toBigDecimal()
    }

    @Test
    fun `inntektJustertForGrunnbeloep runder riktig opp til to desimaler`() {
        inntektJustertForGrunnbeloep(
            pensjonsgivendeInntektIKalenderAaret = 600_000.toBigInteger(),
            gPaaSykmeldingstidspunktet = 124_028.toBigInteger(),
            gjennomsnittligGIKalenderaaret = 104_716.toBigInteger(),
        ) `should be equal to` 710_653.58.toBigDecimal()
    }

    @Test
    fun `justerFor6Gog12Gunder runder verdier under 6G, over 6G og over 12G riktig`() {
        val finnInntekterJustertFor6Gog12G =
            finnInntekterJustertFor6Gog12G(
                grunnbeloepSykmldTidspunkt = 124028,
                mapOf(
                    "2023" to 96_030.76.toBigDecimal(),
                    "2022" to 849_611.91.toBigDecimal(),
                    "2021" to 2_741_113.87.toBigDecimal(),
                ),
            )
        finnInntekterJustertFor6Gog12G.avrundetTilToDesimaler() `should be equal to`
            mapOf(
                "2023" to 96_030.76.toBigDecimal(),
                "2022" to 779_315.97.toBigDecimal(),
                "2021" to 992_224.00.toBigDecimal(),
            ).avrundetTilToDesimaler()
    }

    @Test
    fun `justerFor6Gog12Gunder runder grenseverdier riktig`() {
        val finnInntekterJustertFor6Gog12G =
            finnInntekterJustertFor6Gog12G(
                grunnbeloepSykmldTidspunkt = 100_000,
                mapOf(
                    "2023" to 100_000.00.toBigDecimal(),
                    "2022" to 690_000.00.toBigDecimal(),
                    "2021" to 2_000_000.00.toBigDecimal(),
                ),
            )
        finnInntekterJustertFor6Gog12G.avrundetTilToDesimaler() `should be equal to`
            mapOf(
                "2023" to 100_000.00.toBigDecimal(),
                "2022" to 630_000.00.toBigDecimal(),
                "2021" to 800_000.00.toBigDecimal(),
            ).avrundetTilToDesimaler()
    }

    @Test
    fun `beregnGjennomsnittligInntekt avrundes riktig når tallene kan deles på tre`() {
        val justerteInntekter =
            mapOf(
                "2016" to 90_000.00.toBigDecimal(),
                "2015" to 90_000.00.toBigDecimal(),
                "2014" to 90_000.00.toBigDecimal(),
            )
        beregnGjennomsnittligInntekt(justerteInntekter) `should be equal to` 90_000.00.toBigDecimal()
    }

    @Test
    fun `beregnGjennomsnittligInntekt beregnes og avrundes riktig til to desimaler`() {
        val justerteInntekter =
            mapOf(
                "2016" to 623_385.67.toBigDecimal(),
                "2015" to 600_417.33.toBigDecimal(),
                "2014" to 543_613.00.toBigDecimal(),
            )
        beregnGjennomsnittligInntekt(justerteInntekter) `should be equal to` 589_138.67.toBigDecimal()
    }

    @Test
    fun `beregnEndring25Prosent av heltall returneres som riktig heltall`() {
        val beregnetGrunnlag1 = 500_000.0.toBigDecimal()
        beregnEndring25Prosent(beregnetGrunnlag1).let {
            // 500_000 * 1.25 = 625_000
            it.p25 `should be equal to` 625_000.toBigInteger()
            // 500_000 * 0.75 = 375_000
            it.m25 `should be equal to` 375_000.toBigInteger()
        }
    }

    @Test
    fun `beregnEndring25Prosent av desimaltall rundes til riktig heltall`() {
        beregnEndring25Prosent(589_138.67.toBigDecimal()).let {
            // 589_138.67 * 1.25 = 736_423,34
            it.p25 `should be equal to` 736_423.toBigInteger()
            // 589_138.67 * 0.75 = 441_854,00
            it.m25 `should be equal to` 441_854.toBigInteger()
        }
    }

    @Test
    fun `beregnEndring25Prosent runder riktig opp og ned`() {
        beregnEndring25Prosent(500_001.0.toBigDecimal()).let {
            // 500_001 * 1.25 = 625_001.25
            it.p25 `should be equal to` 625_001.toBigInteger()
            // 500_001 * 0.75 = 375_000.75
            it.m25 `should be equal to` 375_001.toBigInteger()
        }
    }

    private fun Map<String, BigDecimal>.avrundetTilToDesimaler(): Map<String, BigDecimal> =
        this.mapValues {
            it.value.setScale(2, RoundingMode.HALF_UP)
        }
}
