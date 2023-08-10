package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.repository.LockRepository
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
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

    private fun sendInnSykmelding(fom: LocalDate, tom: LocalDate) {
        val sykmeldingKafkaMessage = sykmeldingKafkaMessage(
            fnr = fnr,
            sykmeldingsperioder = heltSykmeldt(
                fom = fom,
                tom = tom
            )
        )
        val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(
            sykmeldingId,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom }
        )
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC
        )
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

                    forsteAdvisoryLockResponse = lockRepository.settAdvisoryLock(fnr.toLong())

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

                    andreAdvisoryLockResponse = lockRepository.settAdvisoryLock(fnr.toLong())
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
            lockRepository.settAdvisoryLock(fnr.toLong()) shouldBeEqualTo true
        }
    }

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        sendInnSykmelding(
            fom = basisdato.minusDays(1),
            tom = basisdato.plusDays(15)
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1
        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(2)
    fun `Sykmelding blir ikke behandlet når advisory lock er opptatt`() {
        val ventTilLockErSatt = CompletableFuture<Any>()
        val holdAdvisoryLock = CompletableFuture<Any>()
        var threadException: Throwable? = null

        val advisoryThread = thread {
            runCatching {
                doInTransaction {
                    lockRepository.settAdvisoryLock(fnr.toLong(), fnr.toLong() + 2, fnr.toLong() + 3)
                    ventTilLockErSatt.complete(Any())
                    holdAdvisoryLock.get()
                }
            }.onFailure {
                threadException = it
            }
        }
        ventTilLockErSatt.get() // Tråden må ha startet en transactionen før vi går videre

        val exception = assertThrows<RuntimeException> {
            sendInnSykmelding(
                fom = basisdato,
                tom = basisdato.plusDays(15)
            )
        }
        exception.message shouldNotBe null
        exception.message!! shouldContain "Det finnes allerede en advisory lock for sykmelding"

        holdAdvisoryLock.complete(Any())
        advisoryThread.join()
        threadException shouldBeEqualTo null

        hentSoknaderMetadata(fnr) shouldHaveSize 1
    }

    @Test
    @Order(3)
    fun `Sykmelding klipper når advisory lock er ledig`() {
        sendInnSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15)
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val soknaderMetadata = hentSoknaderMetadata(fnr)
        soknaderMetadata shouldHaveSize 2

        val forsteSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        forsteSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        forsteSoknad.status shouldBeEqualTo RSSoknadstatus.NY
        forsteSoknad.fom shouldBeEqualTo basisdato.minusDays(1)
        forsteSoknad.tom shouldBeEqualTo basisdato.minusDays(1)

        val fremtidigSoknad = hentSoknad(
            soknadId = soknaderMetadata.last().id,
            fnr = fnr
        )
        fremtidigSoknad.soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        fremtidigSoknad.status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        fremtidigSoknad.fom shouldBeEqualTo basisdato
        fremtidigSoknad.tom shouldBeEqualTo basisdato.plusDays(15)
    }
}
