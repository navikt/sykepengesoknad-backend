package no.nav.syfo.exception

import org.springframework.http.HttpStatus

class FeilStatusForOppdaterSporsmalException(message: String) : AbstractApiError(
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST,
    reason = "FEIL_STATUS_FOR_OPPDATER_SPORSMAL",
    loglevel = LogLevel.WARN
)
