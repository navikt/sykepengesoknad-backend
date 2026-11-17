package no.nav.helse.flex.testoppsett

import no.nav.helse.flex.outbox.OutboxService
import no.nav.helse.flex.outbox.OutboxServiceImpl
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Den ekte outboxen, men OutboxJob kan pauses. Alle testklassene i FellesTestOppsett deler én Spring-kontekst
 * og dermed én OutboxJob, så tester som styrer sendingen selv må pause jobben for at den ikke skal ta radene.
 */
@Service
@Primary
@Profile("!fakes")
class PausbarOutboxService(
    private val outboxServiceImpl: OutboxServiceImpl,
) : OutboxService by outboxServiceImpl {
    private val lås = ReentrantReadWriteLock()

    @Volatile
    private var jobbPauset = false

    // Venter til en pågående sending er ferdig, så jobben ikke rører rader testen lager etterpå.
    fun pauseJobb() = lås.write { jobbPauset = true }

    fun startJobb() {
        jobbPauset = false
    }

    override fun sendUsendteForEldsteLedigeFnr(): Boolean = lås.read { !jobbPauset && outboxServiceImpl.sendUsendteForEldsteLedigeFnr() }
}
