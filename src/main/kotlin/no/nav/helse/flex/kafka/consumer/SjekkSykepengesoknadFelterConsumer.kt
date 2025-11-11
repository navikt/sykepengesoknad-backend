package no.nav.helse.flex.kafka.consumer

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SjekkSykepengesoknadFelterConsumer {
    private val log = logger()

    private var resultatErLogget: Boolean = false
    private val felterVedSoknadstype: MutableMap<SoknadstypeDTO, MutableMap<String, Int>> = mutableMapOf()

    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "temp-sjekk-sykepengesoknad-felter-consumer",
        concurrency = "1",
    )
    fun listen(cr: ConsumerRecord<String, String?>) {
        val value = cr.value()
        if (value == null) {
            return
        }
        val sykepengesoknad =
            try {
                konverter(value)
            } catch (ex: Exception) {
                log.warn("Feil ved konvertering av sykepengesoknad med id=${cr.key()}")
                return
            }

        try {
            behandle(sykepengesoknad)
        } catch (ex: Exception) {
            log.warn("Feil ved behandling av sykepengesoknad med id=${cr.key()}")
            return
        }
    }

    fun behandle(soknad: SykepengesoknadDTO) {
        soknad.opprettet?.let { opprettet ->
            if (opprettet > LocalDateTime.parse("2025-11-10T00:00:00")) {
                if (!resultatErLogget) {
                    log.info("Felter satt på soknader ved type: " + felterVedSoknadstype)
                    resultatErLogget = true
                }
                return
            }
        }

        val soknadstype = soknad.type
        val dynamiskSoknad: Map<String, Any?> = objectMapper.readValue(objectMapper.writeValueAsString(soknad))

        val felterForSoknadstype = felterVedSoknadstype.getOrPut(soknadstype) { mutableMapOf() }
        for ((key, value) in dynamiskSoknad) {
            if (value != null) {
                felterForSoknadstype[key] = felterForSoknadstype.getOrDefault(key, 0) + 1
            }
        }
    }

    fun konverter(serialized: String): SykepengesoknadDTO = objectMapper.readValue(serialized)
}
