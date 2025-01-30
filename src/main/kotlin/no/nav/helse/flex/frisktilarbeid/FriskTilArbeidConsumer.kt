package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.core.JacksonException
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Profile("frisktilarbeid")
@Component
class FriskTilArbeidConsumer(
    private val friskTilArbeidService: FriskTilArbeidService,
) {
    val log = logger()

    @KafkaListener(
        topics = [FRISKTILARBEID_TOPIC],
        id = "flex-frisktilarbeid-dev-2",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("Mottok FriskTilArbeidVedtakStatus med key: ${cr.key()}.")

        try {
            friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
                FriskTilArbeidVedtakStatusKafkaMelding(
                    cr.key(),
                    cr.value().tilFriskTilArbeidVedtakStatus(),
                ),
            )
            acknowledgment.acknowledge()
        } catch (e: JacksonException) {
            log.error("Klarte ikke å deserialisere FriskTilArbeidVedtakStatus", e)
            throw e
        } catch (e: Exception) {
            log.error("Feilet ved mottak av FriskTilArbeidVedtakStatus", e)
            throw e
        }
    }
}
