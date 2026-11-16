package no.nav.helse.flex.frisktilarbeid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldBeEmpty
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.fakes.FriskTilArbeidRepositoryFake
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.core.exc.StreamReadException

@ExtendWith(MockitoExtension::class)
class FriskTilArbeidConsumerTest {
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepositoryFake

    @Mock
    private lateinit var environmentToggles: EnvironmentToggles

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    private lateinit var friskTilArbeidConsumer: FriskTilArbeidConsumer

    @BeforeEach
    fun setUp() {
        friskTilArbeidRepository = spy(FriskTilArbeidRepositoryFake())
        val service = FriskTilArbeidService(friskTilArbeidRepository, mock<FriskTilArbeidSoknadService>())
        friskTilArbeidConsumer = FriskTilArbeidConsumer(service, environmentToggles)
    }

    @Test
    fun `Acker melding ved vellykket prosessering`() {
        val consumerRecord = gyldigConsumerRecord()

        friskTilArbeidConsumer.listen(consumerRecord, acknowledgment)

        friskTilArbeidRepository.forventVedtakLagretFraKafkaMelding(consumerRecord)
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `Acker melding i ikke-produksjon ved feil under prosessering`() {
        val consumerRecord = gyldigConsumerRecord()
        whenever(environmentToggles.isNotProduction()).thenReturn(true)
        whenever(environmentToggles.environment()).thenReturn("q1")
        doThrow(IllegalStateException("Feil ved lagring")).whenever(friskTilArbeidRepository).save(any<FriskTilArbeidVedtakDbRecord>())

        friskTilArbeidConsumer.listen(consumerRecord, acknowledgment)

        verify(acknowledgment, times(1)).acknowledge()
        friskTilArbeidRepository.findAll().shouldBeEmpty()
    }

    @Test
    fun `Kaster feil i produksjon ved feil under prosessering`() {
        val consumerRecord = gyldigConsumerRecord()
        whenever(environmentToggles.isNotProduction()).thenReturn(false)
        val lagringsfeil = IllegalStateException("Feil ved lagring")
        doThrow(lagringsfeil).whenever(friskTilArbeidRepository).save(any<FriskTilArbeidVedtakDbRecord>())

        val feil = assertThrows<IllegalStateException> { friskTilArbeidConsumer.listen(consumerRecord, acknowledgment) }

        assertSame(lagringsfeil, feil)
        verify(acknowledgment, never()).acknowledge()
        friskTilArbeidRepository.findAll().shouldBeEmpty()
    }

    @Test
    fun `Acker melding i ikke-produksjon ved ugyldig payload`() {
        whenever(environmentToggles.isNotProduction()).thenReturn(true)
        whenever(environmentToggles.environment()).thenReturn("dev")

        friskTilArbeidConsumer.listen(ugyldigConsumerRecord(), acknowledgment)

        friskTilArbeidRepository.findAll().shouldBeEmpty()
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `Kaster feil i produksjon ved ugyldig payload`() {
        whenever(environmentToggles.isNotProduction()).thenReturn(false)

        assertThrows<StreamReadException> {
            friskTilArbeidConsumer.listen(ugyldigConsumerRecord(), acknowledgment)
        }

        friskTilArbeidRepository.findAll().shouldBeEmpty()
        verify(acknowledgment, never()).acknowledge()
    }

    private fun gyldigConsumerRecord(): ConsumerRecord<String, String> {
        val fnr = "11111111111"
        val key = fnr.asProducerRecordKey()
        val payload = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET).serialisertTilString()
        return ConsumerRecord("topic", 0, 0L, key, payload)
    }

    private fun ugyldigConsumerRecord(): ConsumerRecord<String, String> = ConsumerRecord("topic", 0, 0L, "key", "{ugyldig")
}
