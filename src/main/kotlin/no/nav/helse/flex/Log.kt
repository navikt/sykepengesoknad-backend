package no.nav.helse.flex

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.util.UUID

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

object LogMarker {
    val TEAM_LOG: Marker = MarkerFactory.getMarker("TEAM_LOG")
}

fun Logger.warnSecure(
    message: String,
    secureMessage: String,
    secureThrowable: Throwable? = null,
) {
    val teamLogId = UUID.randomUUID().toString().take(8)
    this.warn("$message (TeamLogId: $teamLogId)")
    this.warn(LogMarker.TEAM_LOG, "$message (TeamLogId: $teamLogId) $secureMessage", secureThrowable)
}
