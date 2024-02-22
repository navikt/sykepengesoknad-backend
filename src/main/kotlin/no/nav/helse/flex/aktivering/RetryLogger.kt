package no.nav.helse.flex.aktivering

import no.nav.helse.flex.logger
import org.slf4j.Logger
import org.springframework.stereotype.Service

const val MAX_RETRIES_BEFORE_ERROR = 5

@Service
class RetryLogger(
    private val retryRepository: RetryRepository,
) {
    private final val log = logger()

    fun inkrementerRetriesOgReturnerLogger(id: String): LoggerFunction {
        try {
            val retries = retryRepository.inkrementerRetries(id)
            // Det ville sett mye bedre å returnerer en lambda av typen (String, Throwable) -> Unit
            // men vi trenger klasser for å kunne asserte type i testene.
            return if (retries.retryCount > MAX_RETRIES_BEFORE_ERROR) {
                ErrorLogger(log)
            } else {
                WarnLogger(log)
            }
        } catch (e: Exception) {
            // Sikrer at vi får returnert en logger selv om noe feiler ved inkrementering.
            log.error("Feil ved inkrementering av retries for sykepengesoknad $id.", e)
            return ErrorLogger(log)
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
