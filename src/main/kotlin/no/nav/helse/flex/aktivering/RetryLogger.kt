package no.nav.helse.flex.aktivering

import no.nav.helse.flex.logger
import org.slf4j.Logger
import org.springframework.stereotype.Service

const val MAX_RETRIES_BEFORE_ERROR = 5

@Service
class RetryLogger {
    private final val log = logger()

    // TODO: Bytt ut med databasebackend.
    private val retries = mutableMapOf<String, Int>()

    fun inkrementerRetriesOgReturnerLogger(id: String): LoggerFunction {
        retries[id] = retries.getOrDefault(id, 0) + 1
        val antallRetries = retries[id] ?: 0

        // Det ville sett mye bedre å returnerer en lambda av typen (String, Throwable) -> Unit
        // men vi trenger klasser for å kunne asserte på noe i testene.
        return if (antallRetries > MAX_RETRIES_BEFORE_ERROR) {
            ErrorLogger(log)
        } else {
            WarnLogger(log)
        }
    }
}

interface LoggerFunction {
    fun log(
        message: String,
        throwable: Throwable,
    )
}

class WarnLogger(private val log: Logger) : LoggerFunction {
    override fun log(
        message: String,
        throwable: Throwable,
    ) {
        log.warn(message, throwable)
    }
}

class ErrorLogger(private val log: Logger) : LoggerFunction {
    override fun log(
        message: String,
        throwable: Throwable,
    ) {
        log.error(message, throwable)
    }
}
