package no.nav.helse.flex.juridiskvurdering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class JuridiskVurderingKafkaProducer(
    private val producer: KafkaProducer<String, JuridiskVurderingKafkaDto>,
    @param:Value("\${nais.app.name}")
    private val naisAppName: String,
    @param:Value("\${nais.app.image}")
    private val naisAppImage: String,
) {
    val log = logger()

    @WithSpan
    fun produserMelding(dto: JuridiskVurderingKafkaDto) {
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

    fun tilDto(juridiskVurdering: JuridiskVurdering): JuridiskVurderingKafkaDto =
        JuridiskVurderingKafkaDto(
            bokstav = juridiskVurdering.bokstav,
            fodselsnummer = juridiskVurdering.fodselsnummer,
            sporing = juridiskVurdering.sporing,
            lovverk = juridiskVurdering.lovverk,
            lovverksversjon = juridiskVurdering.lovverksversjon,
            paragraf = juridiskVurdering.paragraf,
            ledd = juridiskVurdering.ledd,
            punktum = juridiskVurdering.punktum,
            input = juridiskVurdering.input,
            output = juridiskVurdering.output,
            utfall = juridiskVurdering.utfall,
            id = UUID.randomUUID().toString(),
            eventName = "subsumsjon",
            versjon = "1.0.0",
            kilde = naisAppName,
            versjonAvKode = naisAppImage,
            tidsstempel = Instant.now(),
        )
}

const val JURIDISK_VURDERING_TOPIC = "flex.omrade-helse-etterlevelse"
