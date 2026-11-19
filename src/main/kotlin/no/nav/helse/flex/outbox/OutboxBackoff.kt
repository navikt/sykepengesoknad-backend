package no.nav.helse.flex.outbox

import java.time.Duration
import kotlin.math.pow

internal val MAKS_VENTETID: Duration = Duration.ofMinutes(30)

/**
 * Ventetid før neste forsøk etter at sending har feilet [antallForsok] ganger.
 * Starter på 1 sekund og dobles for hvert forsøk, til maks 30 minutter:
 * 1s, 2s, 4s, 8s, 16s, 32s, 1m4s, 2m8s, 4m16s, 8m32s, 17m4s, 30m.
 */
internal fun beregnNesteOutboxForsøk(antallForsok: Int): Duration {
    val eksponent = (antallForsok - 1).coerceIn(0, 11)
    val sekunder = 2.0.pow(eksponent).coerceAtMost(MAKS_VENTETID.seconds.toDouble())
    return Duration.ofSeconds(sekunder.toLong())
}
