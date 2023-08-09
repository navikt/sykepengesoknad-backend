package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.repository.LockRepository
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConcurrentKlipp : BaseTestClass() {
    @Autowired
    private lateinit var lockRepository: LockRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        transactionTemplate = TransactionTemplate(transactionManager)
    }

    private fun doInTransaction(function: () -> Unit) {
        transactionTemplate.execute {
            function()
        }
    }

    private final val basisdato = LocalDate.now()
    private val fnr = "11555555555"

    @Test
    @Order(0)
    fun `Sjekk at advisory lock settes og slippes`() {
        val ventTilForsteHarStartet = CompletableFuture<Any>()
        val ventTilAndreErFerdig = CompletableFuture<Any>()
        lateinit var forsteTransactionStart: Instant
        lateinit var andreTransactionStart: Instant
        var forsteAdvisoryLockResponse: Boolean? = null
        var andreAdvisoryLockResponse: Boolean? = null
        var forsteTransactionException: Throwable? = null
        var andreTransactionException: Throwable? = null

        val forsteThread = thread {
            runCatching {
                doInTransaction {
                    forsteTransactionStart = Instant.now()

                    forsteAdvisoryLockResponse = lockRepository.settAdvisoryLock(fnr)

                    ventTilForsteHarStartet.complete(Any()) // Den andre transactionen kan startes
                    ventTilAndreErFerdig.get() // Vi holder på transactionen til neste er ferdig
                }
            }.onFailure {
                forsteTransactionException = it
            }
        }

        val andreThread = thread {
            runCatching {
                ventTilForsteHarStartet.get() // Første tråden må ha startet en transactionen før vi går videre

                doInTransaction {
                    andreTransactionStart = Instant.now()

                    andreAdvisoryLockResponse = lockRepository.settAdvisoryLock(fnr)
                }
            }.onFailure {
                andreTransactionException = it
            }

            ventTilAndreErFerdig.complete(Any()) // Første transactionen kan fortsette
        }

        forsteThread.join()
        andreThread.join()

        forsteTransactionStart shouldBeBefore andreTransactionStart

        forsteAdvisoryLockResponse shouldBeEqualTo true
        andreAdvisoryLockResponse shouldBeEqualTo false

        forsteTransactionException shouldBeEqualTo null
        andreTransactionException shouldBeEqualTo null

        // Sjekker at låsen nå er ledig
        doInTransaction {
            lockRepository.settAdvisoryLock(fnr) shouldBeEqualTo true
        }
    }
}
