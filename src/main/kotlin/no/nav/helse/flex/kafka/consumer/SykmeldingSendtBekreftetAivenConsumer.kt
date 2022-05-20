package no.nav.helse.flex.kafka.consumer

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.kafka.getSafeNavCallIdHeaderAsString
import no.nav.syfo.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

const val SYKMELDINGSENDT_TOPIC = "teamsykmelding." + "syfo-sendt-sykmelding"
const val SYKMELDINGBEKREFTET_TOPIC = "teamsykmelding." + "syfo-bekreftet-sykmelding"

@Component
@Profile("sykmeldinger")
class SykmeldingSendtBekreftetAivenConsumer(
    private val behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService
) {

    val log = logger()

    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC, SYKMELDINGBEKREFTET_TOPIC],
        id = "sykmelding-sendt-bekreftet",
        idIsGroup = false,
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        MDC.put(NAV_CALLID, getSafeNavCallIdHeaderAsString(cr.headers()))
        val melding = cr.value()?.tilSykmeldingKafkaMessage()

        try {
            behandleSendtBekreftetSykmeldingService.prosesserSykmelding(cr.key(), melding)
            val msBehandling = Instant.now().toEpochMilli() - cr.timestamp()
            if (msBehandling > 10000) {
                log.warn("Brukte $msBehandling millisekunder på å behandle søknadsopprettelse for sykmelding ${cr.key()}")
            }
            acknowledgment.acknowledge()
        } finally {
            MDC.remove(NAV_CALLID)
        }
    }
}

fun String.tilSykmeldingKafkaMessage(): SykmeldingKafkaMessage = OBJECT_MAPPER.readValue(this)
