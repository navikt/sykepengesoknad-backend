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
): BigInteger {
    return BigDecimal(pensjonsgivendeInntektIKalenderAaret)
        .times(BigDecimal(gPaaSykmeldingstidspunktet))
        // Bruker divide() siden div() bruker RoundingMode.HALF_EVEN.
        .divide(
            // Runder til 0 med BigDecimal og HALF_UP siden toBigInteger() alltid runder ned.
            BigDecimal(gjennomsnittligGIKalenderaaret),
            0,
            RoundingMode.HALF_UP,
        ).toBigInteger()
}
// ) = (pensjonsgivendeInntektIKalenderAaret * gPaaSykmeldingstidspunktet) / gjennomsnittligGIKalenderaaret

/**
 * Justerer inntekter basert på formel der inntekt mellom 6G og 12G reduseres til 1/3,
 * og inntekt over 12G blir trukket fra
 */
fun finnInntekterJustertFor6Gog12G(
    justertInntektForGPerAar: Map<String, BigInteger>,
    grunnbeloepSykmldTidspunkt: Int,
): MutableMap<String, BigDecimal> {
    val g12 = BigDecimal(grunnbeloepSykmldTidspunkt * 12)
    val g6 = BigDecimal(grunnbeloepSykmldTidspunkt * 6)

    val justerteInntekter =
        justertInntektForGPerAar.mapValues {
            it.value.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
        }.toMutableMap()

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
    return justerteInntekter
}

fun beregnGjennomsnittligInntekt(justerteInntekter: Map<String, BigDecimal>): BigDecimal {
    val snittVerdi = justerteInntekter.values.sumOf { it }.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
    return snittVerdi.setScale(2, RoundingMode.HALF_UP)
}

fun beregnEndring25Prosent(snittPGI: BigDecimal): Beregnet {
    return Beregnet(
        snitt = snittPGI.roundToBigInteger(),
        p25 = (snittPGI * BigDecimal("1.25")).roundToBigInteger(),
        m25 = (snittPGI * BigDecimal("0.75")).roundToBigInteger(),
    )
}

fun BigDecimal.roundToBigInteger(): BigInteger {
    return this.setScale(0, RoundingMode.HALF_UP).toBigInteger()
}
