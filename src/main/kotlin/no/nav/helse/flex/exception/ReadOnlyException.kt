package no.nav.helse.flex.exception

import org.springframework.http.HttpStatus

class ReadOnlyException() : AbstractApiError(
    message = "Read only",
    httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
    reason = "READONLY",
    loglevel = LogLevel.OFF,
)
