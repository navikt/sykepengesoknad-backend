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
}
