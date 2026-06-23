package no.nav.helse.flex.juridiskvurdering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import java.util.concurrent.Future

@Component
class JuridiskVurderingKafkaProducer(
    private val producer: KafkaProducer<String, JuridiskVurderingKafkaDto>,
    @param:Value("\${nais.app.name}")
    private val naisAppName: String,
    @param:Value("\${nais.app.image}")
    private val naisAppImage: String,
) {
    val log = logger()

    // Sender meldingen asynkront og returnerer en Future slik at kalleren kan vente på bekreftelse
    // fra broker etter at alle meldinger er sendt.
    @WithSpan
    fun produserMeldingAsynkront(juridiskVurdering: JuridiskVurdering): Future<RecordMetadata> {
        val dto = juridiskVurdering.tilDto()
        return producer.send(ProducerRecord(JURIDISK_VURDERING_TOPIC, dto.fodselsnummer, dto)) { _, e ->
            if (e != null) {
                log.warn(
                    "Uventet exception ved publisering av juridiskvurdering ${dto.id} på topic $JURIDISK_VURDERING_TOPIC",
                    e,
                )
            }
        }
    }

    @WithSpan
    fun produserMelding(juridiskVurdering: JuridiskVurdering) {
        val dto = juridiskVurdering.tilDto()
        try {
            producer
                .send(
                    ProducerRecord(
                        JURIDISK_VURDERING_TOPIC,
                        dto.fodselsnummer,
                        dto,
                    ),
                ).get()
        } catch (e: Throwable) {
            log.warn(
                "Uventet exception ved publisering av juridiskvurdering ${dto.id} på topic $JURIDISK_VURDERING_TOPIC",
                e,
            )
            // get() kaster InterruptedException eller ExecutionException. Begge er checked, så pakker  de den inn i
            // en RuntimeException da en CheckedException kan forhindre rollback i metoder annotert med @Transactional.
            throw RuntimeException(e)
        }
    }

    fun JuridiskVurdering.tilDto(): JuridiskVurderingKafkaDto =
        JuridiskVurderingKafkaDto(
            bokstav = bokstav,
            fodselsnummer = fodselsnummer,
            sporing = sporing,
            lovverk = lovverk,
            lovverksversjon = lovverksversjon,
            paragraf = paragraf,
            ledd = ledd,
            punktum = punktum,
            input = input,
            output = output,
            utfall = utfall,
            id = UUID.randomUUID().toString(),
            eventName = "subsumsjon",
            versjon = "1.0.0",
            kilde = naisAppName,
            versjonAvKode = naisAppImage,
            tidsstempel = Instant.now(),
        )
}

const val JURIDISK_VURDERING_TOPIC = "flex.omrade-helse-etterlevelse"
