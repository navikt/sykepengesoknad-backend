package no.nav.helse.flex.exception

import org.springframework.http.HttpStatus

class ForsokPaSendingAvNyereSoknadException(
    message: String,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.BAD_REQUEST,
        reason = "FORSOK_PA_SENDING_AV_NYERE_SOKNAD",
        loglevel = LogLevel.WARN,
    )
