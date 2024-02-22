package no.nav.helse.flex.aktivering

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be instance of`
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RetryLoggerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var retryLogger: RetryLogger

    @Autowired
    private lateinit var retryRepository: RetryRepository

    @BeforeAll
    fun setUp() {
        retryRepository.deleteAll()
    }

    @Test
    fun `ErrorLogger skal returneres når antall kall overstiger MAX_WARNINGS med en gitt ID`() {
        repeat(MAX_RETRIES_BEFORE_ERROR) {
            retryLogger.inkrementerRetriesOgReturnerLogger("id-1") `should be instance of` WarnLogger::class
        }
        retryLogger.inkrementerRetriesOgReturnerLogger("id-1") `should be instance of` ErrorLogger::class
        retryLogger.inkrementerRetriesOgReturnerLogger("id-2") `should be instance of` WarnLogger::class
    }
}
