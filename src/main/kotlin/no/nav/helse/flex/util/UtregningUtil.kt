package no.nav.helse.flex.util

import java.math.BigInteger
import kotlin.math.roundToInt

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
 * Dersom snittet er over 6G fastsettes sykepengegrunnlaget til 6G
 * @return gjennomsnittlig inntekt, fastsatt sykepengegrunnlag
 */
fun beregnGjennomsnittligInntekt(
    beregnetInntektPerAar: Map<String, BigInteger>,
    grunnbeloepSykmldTidspunkt: Int,
): Pair<BigInteger, BigInteger> {
    val justerteInntekter = beregnetInntektPerAar.toMutableMap()
    val g12 = grunnbeloepSykmldTidspunkt.toBigInteger() * 12.toBigInteger()
    val g6 = grunnbeloepSykmldTidspunkt.toBigInteger() * 6.toBigInteger()

    val verdierPaa12G =
        beregnetInntektPerAar.filterValues { it >= g12 }.map {
            it.key to g12
        }.toMap()
    justerteInntekter.putAll(verdierPaa12G)

    val reduserteVerdierMellom6og12G =
        justerteInntekter.filterValues { it in g6..g12 }.map {
            it.key to g6 + (it.value - g6) / 3.toBigInteger()
        }
    justerteInntekter.putAll(reduserteVerdierMellom6og12G)

    val snittVerdi = justerteInntekter.values.sumOf { it } / 3.toBigInteger()
    val fastsattSykepengegrunnlag = if (snittVerdi > g6) g6 else snittVerdi

    return Pair(snittVerdi, fastsattSykepengegrunnlag)
}

fun beregnEndring25Prosent(snittPGI: BigInteger): List<BigInteger> {
    return listOf(
        (snittPGI.toDouble() * 0.75).roundToInt().toBigInteger(),
        (snittPGI.toDouble() * 1.25).roundToInt().toBigInteger(),
    )
}
