package no.nav.helse.flex.juridiskvurdering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.amshove.kluent.`should be instance of`
import org.apache.kafka.clients.producer.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class JuridiskVurderingKafkaProducerTest {
    @Test
    fun `skal wrappe checked exception til runtime exception`() {
        val mockedProducer: KafkaProducer<String, JuridiskVurderingKafkaDto> = mock()

        val future: CompletableFuture<RecordMetadata> = mock()
        doThrow(ExecutionException("checked", RuntimeException("unchecked"))).whenever(future).get()

        whenever(mockedProducer.send(any())).thenAnswer { future }

        val jurifiskVurderingKafkaProducer =
            JuridiskVurderingKafkaProducer(mockedProducer, "app", "image-1234.docker")

        val kastetException =
            assertThrows(RuntimeException::class.java) {
                jurifiskVurderingKafkaProducer.produserMelding(juridiskVurdering)
            }

        kastetException.cause `should be instance of` ExecutionException::class
    }

    val juridiskVurdering =
        JuridiskVurdering(
            fodselsnummer = "fnr",
            sporing = emptyMap(),
            input = emptyMap(),
            output = emptyMap(),
            lovverk = "folketrygdloven",
            paragraf = "8-17",
            ledd = 1,
            bokstav = "a",
            punktum = null,
            lovverksversjon = LocalDate.of(2018, 1, 1),
            utfall = Utfall.VILKAR_BEREGNET,
        )
}
