package no.nav.helse.flex.exception

import org.springframework.http.HttpStatus

abstract class AbstractApiError(
    message: String,
    val httpStatus: HttpStatus,
    val reason: String,
    val loglevel: LogLevel,
    grunn: Throwable? = null,
) : RuntimeException(message, grunn)

enum class LogLevel {
    WARN,
    ERROR,
    OFF,
}
