package no.nav.helse.flex.aktivering

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.exception.AdvisoryLockConflictException
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_AKTIVERING_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.repository.LockRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.Acknowledgment
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConcurrentAktiveringTest : FellesTestOppsett() {
    @Autowired
    private lateinit var lockRepository: LockRepository

    @Autowired
    private lateinit var soknadAktivering: SoknadAktivering

    @Autowired
    private lateinit var aktiveringConsumer: AktiveringConsumer

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val fnr = "11666666666"
    private val basisdato = LocalDate.now()

    private lateinit var soknadId: String

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val sykmeldingKafkaMessage =
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(1),
                        tom = basisdato.plusDays(15),
                    ),
            )
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        val soknader = sykepengesoknadRepository.findByFnrIn(listOf(fnr))
        soknader shouldHaveSize 1
        soknader.first().status shouldBeEqualTo Soknadstatus.FREMTIDIG
        soknadId = soknader.first().sykepengesoknadUuid

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(2)
    fun `Aktivering feiler når søknadsopprettelsen holder advisory lock på personen`() {
        medAdvisoryLockHoldtAvAnnenProsess {
            val exception =
                assertThrows<AdvisoryLockConflictException> {
                    soknadAktivering.aktiverSoknad(soknadId)
                }
            exception.message!! shouldContain "Det finnes allerede en advisory lock for soknad $soknadId"
        }

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknadId)!!.status shouldBeEqualTo Soknadstatus.FREMTIDIG
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(3)
    fun `Consumer nacker i stedet for å acke når advisory lock er opptatt`() {
        val acknowledgment = TestAcknowledgment()

        medAdvisoryLockHoldtAvAnnenProsess {
            aktiveringConsumer.listen(aktiveringRecord(), acknowledgment)
        }

        acknowledgment.antallNack shouldBeEqualTo 1
        acknowledgment.antallAck shouldBeEqualTo 0
        acknowledgment.sisteNackSleep shouldBeEqualTo Duration.ofSeconds(1)

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknadId)!!.status shouldBeEqualTo Soknadstatus.FREMTIDIG
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    @Order(4)
    fun `Aktiveringen lykkes når låsen er sluppet`() {
        val acknowledgment = TestAcknowledgment()

        aktiveringConsumer.listen(aktiveringRecord(), acknowledgment)

        acknowledgment.antallAck shouldBeEqualTo 1
        acknowledgment.antallNack shouldBeEqualTo 0

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknadId)!!.status shouldBeEqualTo Soknadstatus.NY

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.first().status shouldBeEqualTo SoknadsstatusDTO.NY
    }

    private fun aktiveringRecord(): ConsumerRecord<String, String> =
        ConsumerRecord(
            SYKEPENGESOKNAD_AKTIVERING_TOPIC,
            0,
            0,
            soknadId,
            AktiveringBestilling(fnr = fnr, soknadId = soknadId).serialisertTilString(),
        )

    private fun medAdvisoryLockHoldtAvAnnenProsess(block: () -> Unit) {
        val låsErSatt = CompletableFuture<Any>()
        val slippLåsen = CompletableFuture<Any>()
        var trådfeil: Throwable? = null

        val låsetråd =
            thread {
                runCatching {
                    TransactionTemplate(transactionManager).execute {
                        lockRepository.settAdvisoryLock(fnr.toLong()) shouldBeEqualTo true
                        låsErSatt.complete(Any())
                        slippLåsen.get()
                    }
                }.onFailure { trådfeil = it }
            }

        låsErSatt.get()
        try {
            block()
        } finally {
            slippLåsen.complete(Any())
            låsetråd.join()
        }
        trådfeil shouldBeEqualTo null
    }

    private class TestAcknowledgment : Acknowledgment {
        var antallAck = 0
        var antallNack = 0
        var sisteNackSleep: Duration? = null

        override fun acknowledge() {
            antallAck++
        }

        override fun nack(sleep: Duration) {
            antallNack++
            sisteNackSleep = sleep
        }
    }
}
