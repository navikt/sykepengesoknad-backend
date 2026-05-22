package no.nav.helse.flex.exception

import org.springframework.http.HttpStatus

class UgyldigOptInSykmeldingException(
    message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.BAD_REQUEST,
        reason = "UGYLDIG_OPT_IN_SYKMELDING",
        loglevel = LogLevel.WARN,
    )
