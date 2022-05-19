package no.nav.syfo.migrering

import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

private const val KORRIGERTE_TOPIC = "flex." + "syfosoknad-korrigerte"

@Component
class KorrigertPatch(
    val sykepengesoknadDAO: SykepengesoknadRepository,
    registry: MeterRegistry,
) {

    private val log = logger()
    val counter = registry.counter("importert_korrigert_counter")

    @KafkaListener(
        topics = [KORRIGERTE_TOPIC],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "importListenerContainerFactory",
    )
    fun listen(records: List<ConsumerRecord<String, String>>, acknowledgment: Acknowledgment) {

        handterSoknader(records.map { it.value() })
        acknowledgment.acknowledge()
    }

    fun handterSoknader(records: List<String>) {
        if (records.isEmpty()) {
            return
        }
        counter.increment(records.size.toDouble())

        sykepengesoknadDAO.findBySykepengesoknadUuidIn(records)
            .filter { it.status != Soknadstatus.KORRIGERT }
            .forEach {

                log.warn("Soknad ${it.sykepengesoknadUuid} med type ${it.soknadstype} har status ${it.status}, endrer til KORRIGERT")
                sykepengesoknadDAO.save(it.copy(status = Soknadstatus.KORRIGERT))
            }
    }
}
