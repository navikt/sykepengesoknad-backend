package no.nav.helse.flex.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * (Pensjonsgivende inntekt i kalenderåret * G på sykmeldingstidspunktet)
 * --------------------------------------------------------------- = Inntekt beregnet for kalenderåret
 *               Gjennomsnittlig G i kalenderåret
 */
fun sykepengegrunnlagUtregner(
    pensjonsgivendeInntektIKalenderAaret: BigInteger,
    gPaaSykmeldingstidspunktet: BigInteger,
    gjennomsnittligGIKalenderaaret: BigInteger,
) = (pensjonsgivendeInntektIKalenderAaret * gPaaSykmeldingstidspunktet) / gjennomsnittligGIKalenderaaret

/**
 * Finner gennomsnittlig inntekt og justerer basert på regler i rundskriv.
 * @return gjennomsnittlig inntekt
 */
fun beregnGjennomsnittligInntekt(
    beregnetInntektPerAar: Map<String, BigInteger>,
    grunnbeloepSykmldTidspunkt: Int,
): BigDecimal {
    val g12 = BigDecimal(grunnbeloepSykmldTidspunkt * 12)
    val g6 = BigDecimal(grunnbeloepSykmldTidspunkt * 6)

    val justerteInntekter = beregnetInntektPerAar.mapValues { it.value.toBigDecimal() }.toMutableMap()

    val verdierPaa12G =
        justerteInntekter.filterValues { it >= g12 }.map {
            it.key to g12
        }.toMap()
    justerteInntekter.putAll(verdierPaa12G)

    val reduserteVerdierMellom6og12G =
        justerteInntekter.filterValues { it in g6..g12 }.map {
            it.key to (g6 + (it.value - g6) / BigDecimal(3)).setScale(2, RoundingMode.HALF_UP)
        }.toMap()
    justerteInntekter.putAll(reduserteVerdierMellom6og12G)

    val snittVerdi = justerteInntekter.values.sumOf { it }.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)

    return snittVerdi.setScale(2, RoundingMode.HALF_UP)
}

fun beregnEndring25Prosent(snittPGI: BigDecimal): List<BigInteger> {
    return listOf(
        (snittPGI * BigDecimal("1.25")).roundToBigInteger(),
        (snittPGI * BigDecimal("0.75")).roundToBigInteger(),
    )
}

fun BigDecimal.roundToBigInteger(): BigInteger {
    return this.setScale(0, RoundingMode.HALF_UP).toBigInteger()
}
