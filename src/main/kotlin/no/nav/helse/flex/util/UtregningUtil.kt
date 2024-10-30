package no.nav.helse.flex.util

import no.nav.helse.flex.service.Beregnet
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * (Pensjonsgivende inntekt i kalenderåret * G på sykmeldingstidspunktet)
 * --------------------------------------------------------------- = Inntekt beregnet for kalenderåret
 *               Gjennomsnittlig G i kalenderåret
 */
fun inntektJustertForGrunnbeloep(
    pensjonsgivendeInntektIKalenderAaret: BigInteger,
    gPaaSykmeldingstidspunktet: BigInteger,
    gjennomsnittligGIKalenderaaret: BigInteger,
): BigDecimal {
    return BigDecimal(pensjonsgivendeInntektIKalenderAaret)
        .times(BigDecimal(gPaaSykmeldingstidspunktet))
        .divide(BigDecimal(gjennomsnittligGIKalenderaaret), 2, RoundingMode.HALF_UP)
}

/**
 * Justerer inntekter basert på formel hvor inntekt mellom 6G og 12G reduseres til 1/3,
 * og inntekt over 12G blir trukket fra.
 */
fun finnInntekterJustertFor6Gog12G(
    grunnbeloepSykmldTidspunkt: Int,
    justertInntektForGPerAar: Map<String, BigDecimal>,
): Map<String, BigDecimal> {
    val g12 = BigDecimal(grunnbeloepSykmldTidspunkt * 12)
    val g6 = BigDecimal(grunnbeloepSykmldTidspunkt * 6)

    val justerteInntekter =
        justertInntektForGPerAar.mapValues { it.value }.toMutableMap()

    val verdierPaa12G =
        justerteInntekter.filterValues { it >= g12 }.map {
            it.key to g12
        }.toMap()
    justerteInntekter.putAll(verdierPaa12G)

    val reduserteVerdierMellom6og12G =
        justerteInntekter.filterValues { it in g6..g12 }.map {
            it.key to (g6 + (it.value - g6).div(BigDecimal(3)))
        }.toMap()
    justerteInntekter.putAll(reduserteVerdierMellom6og12G)
    return justerteInntekter.toMap()
}

fun beregnGjennomsnittligInntekt(justerteInntekter: Map<String, BigDecimal>): BigDecimal {
    return justerteInntekter.values.sumOf { it }.div(BigDecimal(3))
}

fun beregnEndring25Prosent(beregnGjennomsnittligInntekt: BigDecimal): Beregnet {
    return Beregnet(
        snitt = beregnGjennomsnittligInntekt.roundToBigInteger(),
        p25 = (beregnGjennomsnittligInntekt * BigDecimal("1.25")).roundToBigInteger(),
        m25 = (beregnGjennomsnittligInntekt * BigDecimal("0.75")).roundToBigInteger(),
    )
}

fun BigDecimal.roundToBigInteger(): BigInteger {
    return this.setScale(0, RoundingMode.HALF_UP).toBigInteger()
}
