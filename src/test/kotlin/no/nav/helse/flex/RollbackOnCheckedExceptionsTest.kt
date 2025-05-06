package no.nav.helse.flex

import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ExecutionException

class RollbackOnCheckedExceptionsTest : FellesTestOppsett() {
    @Autowired
    private lateinit var mockService: MockService

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    @BeforeEach
    fun slettFraDatabase() {
        medlemskapVurderingRepository.deleteAll()
    }

    @Test
    fun `Transactional ruller ikke tilbake når Kafka kaster Checked Exception`() {
        val e = assertThrows<ExecutionException> { mockService.sendMeldingRullerIkkeTilbake() }
        e `should be instance of` ExecutionException::class

        val findAll = medlemskapVurderingRepository.findAll()
        findAll shouldHaveSize 1
    }

    @Test
    fun `Transactional ruller ikke tilbake når Kafka kaster Checked Exception når vi setter rollbackFor`() {
        val e = assertThrows<ExecutionException> { mockService.sendMeldingRullerTilbake() }
        e `should be instance of` ExecutionException::class

        val findAll = medlemskapVurderingRepository.findAll()
        findAll shouldHaveSize 0
    }
}

@Component
class MockKafkaProducer {
    fun mockSendMelding(): RecordMetadata = throw ExecutionException("ExecutionException", RuntimeException("RuntimeException"))
}

@Service
class MockService(
    private val mockKafkaProducer: MockKafkaProducer,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    @Transactional
    fun sendMeldingRullerIkkeTilbake(): RecordMetadata {
        lagreMelding()
        return mockKafkaProducer.mockSendMelding()
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun sendMeldingRullerTilbake(): RecordMetadata {
        lagreMelding()
        return mockKafkaProducer.mockSendMelding()
    }

    private fun lagreMelding() {
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = Instant.now(),
                svartid = 1L,
                fnr = "12345678910",
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                svartype = "",
                sporsmal = null,
                sykepengesoknadId = UUID.randomUUID().toString(),
            ),
        )
    }
}
