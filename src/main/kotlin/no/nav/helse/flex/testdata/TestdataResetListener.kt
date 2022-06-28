package no.nav.helse.flex.testdata

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("testdatareset")
class TestdataResetListener(val sykepengesoknadDAO: SykepengesoknadDAO) {

    val log = logger()

    @KafkaListener(
        topics = [TESTDATA_RESET_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val fnr = cr.value()
        val antall = sykepengesoknadDAO.nullstillSoknaderMedFnr(fnr)
        log.info("Slettet $antall soknader på fnr $fnr - Key ${cr.key()}")
        acknowledgment.acknowledge()
    }
}

const val TESTDATA_RESET_TOPIC = "flex.testdata-reset"
