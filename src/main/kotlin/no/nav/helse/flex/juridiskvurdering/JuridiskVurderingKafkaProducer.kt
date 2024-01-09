package no.nav.helse.flex.juridiskvurdering

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
    @Value("\${nais.app.name}")
    private val naisAppName: String,
    @Value("\${nais.app.image}")
    private val naisAppImage: String,
) {
    val log = logger()

    fun produserMelding(juridiskVurdering: JuridiskVurdering) {
        val dto = juridiskVurdering.tilDto()
        try {
            producer.send(
                ProducerRecord(
                    juridiskVurderingTopic,
                    dto.fodselsnummer,
                    dto,
                ),
            ).get()
        } catch (e: Throwable) {
            log.warn(
                "Uventet exception ved publisering av juridiskvurdering ${dto.id} på topic $juridiskVurderingTopic",
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

val juridiskVurderingTopic = "flex.omrade-helse-etterlevelse"
