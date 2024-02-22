package no.nav.helse.flex.aktivering

import org.amshove.kluent.`should be instance of`
import org.junit.jupiter.api.Test

class RetryLoggerTest {
    private var retryLogger = RetryLogger()

    @Test
    fun `ErrorLogger skal returneres når antall kall overstiger MAX_WARNINGS med en gitt ID`() {
        repeat(MAX_RETRIES_BEFORE_ERROR) {
            retryLogger.inkrementerRetriesOgReturnerLogger("id-1") `should be instance of` WarnLogger::class
        }
        retryLogger.inkrementerRetriesOgReturnerLogger("id-1") `should be instance of` ErrorLogger::class
        retryLogger.inkrementerRetriesOgReturnerLogger("id-2") `should be instance of` WarnLogger::class
    }
}
