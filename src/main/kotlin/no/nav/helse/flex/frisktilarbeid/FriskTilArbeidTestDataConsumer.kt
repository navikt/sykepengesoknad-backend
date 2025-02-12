package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Profile("frisktilarbeid,testdata")
@Component
class FriskTilArbeidTestDataConsumer(
    private val friskTilArbeidService: FriskTilArbeidService,
    private val friskTilArbeidCronJob: FriskTilArbeidCronJob,
) {
    val log = logger()

    @KafkaListener(
        topics = [FRISKTILARBEID_TESTDATA_TOPIC],
        id = "flex-frisktilarbeid-testdata-1",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
                FriskTilArbeidVedtakStatusKafkaMelding(
                    cr.key(),
                    cr.value().tilFriskTilArbeidVedtakStatus(),
                ),
            )
        } catch (e: Exception) {
            log.error("Feilet ved mottak av FriskTilArbeidVedtakStatus.", e)
        } finally {
            acknowledgment.acknowledge()
            friskTilArbeidCronJob.startBehandlingAvFriskTilArbeidVedtakStatus()
        }
    }
}

const val FRISKTILARBEID_TESTDATA_TOPIC = "flex.test-isfrisktilarbeid-vedtak-status"
