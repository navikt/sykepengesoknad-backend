package no.nav.helse.flex.util

import java.math.BigInteger

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

    return Pair(snittVerdi, g6)
}
