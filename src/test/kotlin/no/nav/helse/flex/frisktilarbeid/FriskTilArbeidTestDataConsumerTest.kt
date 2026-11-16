package no.nav.helse.flex.frisktilarbeid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldBeEmpty
import no.nav.helse.flex.fakes.FriskTilArbeidRepositoryFake
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment

@ExtendWith(MockitoExtension::class)
class FriskTilArbeidTestDataConsumerTest {
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepositoryFake

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    private lateinit var friskTilArbeidTestDataConsumer: FriskTilArbeidTestDataConsumer

    @BeforeEach
    fun setUp() {
        friskTilArbeidRepository = spy(FriskTilArbeidRepositoryFake())
        val service = FriskTilArbeidService(friskTilArbeidRepository, mock<FriskTilArbeidSoknadService>())
        friskTilArbeidTestDataConsumer = FriskTilArbeidTestDataConsumer(service)
    }

    @Test
    fun `Prosesserer gyldig payload og acker melding`() {
        val consumerRecord = gyldigConsumerRecord()

        friskTilArbeidTestDataConsumer.listen(consumerRecord, acknowledgment)

        friskTilArbeidRepository.forventVedtakLagretFraKafkaMelding(consumerRecord)
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `Acker melding selv om prosessering feiler`() {
        doThrow(IllegalStateException("Feil ved lagring")).whenever(friskTilArbeidRepository).save(any<FriskTilArbeidVedtakDbRecord>())

        friskTilArbeidTestDataConsumer.listen(gyldigConsumerRecord(), acknowledgment)

        verify(acknowledgment, times(1)).acknowledge()
        friskTilArbeidRepository.findAll().shouldBeEmpty()
    }

    @Test
    fun `Acker melding ved ugyldig payload`() {
        friskTilArbeidTestDataConsumer.listen(ugyldigConsumerRecord(), acknowledgment)

        friskTilArbeidRepository.findAll().shouldBeEmpty()
        verify(acknowledgment, times(1)).acknowledge()
    }

    private fun gyldigConsumerRecord(): ConsumerRecord<String, String> {
        val fnr = "11111111111"
        val key = fnr.asProducerRecordKey()
        val payload = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET).serialisertTilString()
        return ConsumerRecord("topic", 0, 0L, key, payload)
    }

    private fun ugyldigConsumerRecord(): ConsumerRecord<String, String> = ConsumerRecord("topic", 0, 0L, "key", "{ugyldig")
}
