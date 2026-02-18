import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.kafka.consumer.tilSykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.kafka.getSafeNavCallIdHeaderAsString
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.MDC
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("sykmeldinger")
class LyttPaEnkeltSykmeldingConsumer(
    private val behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering,
) : ConsumerSeekAware {
    val log = logger()
    private val startTidspunkt = Instant.parse("2025-11-01T00:00:00Z").toEpochMilli()
    private var seekCallback: ConsumerSeekAware.ConsumerSeekCallback? = null

    override fun registerSeekCallback(callback: ConsumerSeekAware.ConsumerSeekCallback) {
        seekCallback = callback
    }

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        assignments.keys.forEach { partition ->
            callback.seekToTimestamp(partition.topic(), partition.partition(), startTidspunkt)
            log.info("Setter offset for ${partition.topic()}-${partition.partition()} til timestamp 01.11.2025")
        }
        seekCallback = callback
    }

    @WithSpan
    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC],
        id = "lytt-pa-enkelt-sykmelding",
        idIsGroup = true,
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        MDC.put(NAV_CALLID, getSafeNavCallIdHeaderAsString(cr.headers()))
        val melding = cr.value()?.tilSykmeldingKafkaMessage()

        log.info("Skal ikke gjøre noe med denne meldingen, siden den ikke har riktig sykmelding id: ${cr.key()}")

        if (cr.key() != "31e2f0b8-237e-4da0-8617-60cc71ea4d20") {
            acknowledgment.acknowledge()
            return
        }
        log.info("Skal opprette søknad for sykmelding ${cr.key()}")

        try {
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(cr.key(), melding, cr.topic())
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
