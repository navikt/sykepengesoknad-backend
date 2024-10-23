package no.nav.helse.flex.domain

import java.net.URI
import java.time.Instant

/**
 * @param utførtAv Nav-ident eller fnr på arbeidsgiver
 * @param oppslagPå Fnr på person det gjøres oppslag på, eller organisasjon
 * @param beskrivelse Beskrivelse av hva som er gjort, bør være "menneskelig lesbar"
 */
data class AuditEntry(
    val appNavn: String,
    val utførtAv: String,
    val oppslagPå: String,
    val eventType: EventType,
    val forespørselTillatt: Boolean,
    val oppslagUtførtTid: Instant,
    val beskrivelse: String,
    val requestUrl: URI,
    val requestMethod: String,
)

enum class EventType(val logString: String) {
    CREATE("audit:create"),
    READ("audit:access"),
    UPDATE("audit:update"),
    DELETE("audit:delete"),
}
