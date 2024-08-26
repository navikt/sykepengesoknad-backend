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
