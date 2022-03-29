package no.nav.syfo.exception

import org.springframework.http.HttpStatus

class SporsmalFinnesIkkeISoknadException(message: String) : AbstractApiError(
    message = message,
    httpStatus = HttpStatus.BAD_REQUEST,
    reason = "SPORSMAL_FINNES_IKKE_I_SOKNAD",
    loglevel = LogLevel.WARN
)
