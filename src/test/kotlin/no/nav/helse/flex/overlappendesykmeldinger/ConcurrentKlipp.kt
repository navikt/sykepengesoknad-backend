package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConcurrentKlipp : BaseTestClass() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

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
    fun `Sjekk at kallet foregår i en transaksjon`() {
        doInTransaction { require(TransactionSynchronizationManager.isActualTransactionActive()) }
    }

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(15)
                )
            )
        )
    }

    @Test
    @Order(2)
    fun `Klipper fom og tom samtidig`() {
        val forsteThread: Thread
        val andreThread: Thread
        val ventTilForsteHarStartet = CompletableFuture<Any>()
        val ventTilSisteErFerdig = CompletableFuture<Any>()

        lateinit var forsteTransactionStart: Instant
        lateinit var andreTransactionStart: Instant
        var forsteTransactionException: Throwable? = null
        var andreTransactionException: Throwable? = null

        thread {
            runCatching {
                doInTransaction {
                    forsteTransactionStart = Instant.now()
                    val soknad = hentSoknaderMetadata(fnr).first()

                    ventTilForsteHarStartet.complete(Any()) // Neste tråd kan starte
                    ventTilSisteErFerdig.get() // Vi venter til den andre transactionen er ferdig

                    sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = soknad.id,
                        nyTom = soknad.tom!!.minusDays(1),
                        tom = soknad.tom!!,
                        fom = soknad.fom!!
                    )
                }
            }.onFailure {
                println("Første tråd kastet exception som forventet")
                forsteTransactionException = it
            }
        }.also { forsteThread = it }

        thread {
            runCatching {
                ventTilForsteHarStartet.get() // Første tråden må ha startet en transactionen før vi går videre

                doInTransaction {
                    andreTransactionStart = Instant.now()
                    val soknad = hentSoknaderMetadata(fnr).first()

                    sykepengesoknadDAO.klippSoknadFom(
                        sykepengesoknadUuid = soknad.id,
                        nyFom = soknad.fom!!.plusDays(1),
                        fom = soknad.fom!!,
                        tom = soknad.tom!!
                    )
                }

                ventTilSisteErFerdig.complete(Any())
            }.onFailure {
                println("Andre tråd skal ikke kaste exception")
                andreTransactionException = it
            }
        }.also { andreThread = it }

        forsteThread.join()
        andreThread.join()

        forsteTransactionStart shouldBeBefore andreTransactionStart
        forsteTransactionException!!.message shouldBeEqualTo "Spørringen for å oppdatere tom traff ikke nøyaktig en søknad som forventet!"
        andreTransactionException shouldBeEqualTo null
    }

    @Test
    @Order(3)
    fun `Klipper tom samtidig`() {
        val forsteThread: Thread
        val andreThread: Thread
        val ventTilForsteHarStartet = CompletableFuture<Any>()
        val ventTilSisteErFerdig = CompletableFuture<Any>()

        lateinit var forsteTransactionStart: Instant
        lateinit var andreTransactionStart: Instant
        var forsteTransactionException: Throwable? = null
        var andreTransactionException: Throwable? = null

        thread {
            runCatching {
                doInTransaction {
                    forsteTransactionStart = Instant.now()
                    val soknad = hentSoknaderMetadata(fnr).first()

                    ventTilForsteHarStartet.complete(Any()) // Neste tråd kan starte
                    ventTilSisteErFerdig.get() // Vi venter til den andre transactionen er ferdig

                    sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = soknad.id,
                        nyTom = soknad.tom!!.minusDays(1),
                        tom = soknad.tom!!,
                        fom = soknad.fom!!
                    )
                }
            }.onFailure {
                println("Første tråd kastet exception som forventet")
                forsteTransactionException = it
            }
        }.also { forsteThread = it }

        thread {
            runCatching {
                ventTilForsteHarStartet.get() // Første tråden må ha startet en transactionen før vi går videre

                doInTransaction {
                    andreTransactionStart = Instant.now()
                    val soknad = hentSoknaderMetadata(fnr).first()

                    sykepengesoknadDAO.klippSoknadTom(
                        sykepengesoknadUuid = soknad.id,
                        nyTom = soknad.tom!!.minusDays(1),
                        tom = soknad.tom!!,
                        fom = soknad.fom!!
                    )
                }

                ventTilSisteErFerdig.complete(Any())
            }.onFailure {
                println("Andre tråd skal ikke kaste exception")
                andreTransactionException = it
            }
        }.also { andreThread = it }

        forsteThread.join()
        andreThread.join()

        forsteTransactionStart shouldBeBefore andreTransactionStart
        forsteTransactionException!!.message shouldBeEqualTo "Spørringen for å oppdatere tom traff ikke nøyaktig en søknad som forventet!"
        andreTransactionException shouldBeEqualTo null
    }
}
