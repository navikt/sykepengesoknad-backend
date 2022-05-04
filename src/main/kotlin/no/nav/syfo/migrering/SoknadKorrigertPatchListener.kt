package no.nav.syfo.migrering

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.config.EnvironmentToggles
import no.nav.syfo.domain.Soknadstatus.KORRIGERT
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.kafka.sykepengesoknadTopic
import no.nav.syfo.repository.SykepengesoknadRepository
import no.nav.syfo.util.OBJECT_MAPPER
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SoknadKorrigertPatchListener(
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val environmentToggles: EnvironmentToggles,
) {

    @KafkaListener(
        topics = [sykepengesoknadTopic],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "korrigertPatchListenerContainerFactory",
    )
    fun listen(records: List<ConsumerRecord<String, String>>, acknowledgment: Acknowledgment) {

        handterSoknader(records.map { it.value() })
        acknowledgment.acknowledge()
    }

    fun handterSoknader(records: List<String>) {
        records
            .map { it.tilSykepengesoknadDto() }
            .forEach { it.patchKorrigerer() }
    }

    private fun SykepengesoknadDTO.patchKorrigerer() {
        if (status != SoknadsstatusDTO.SENDT) {
            return
        }
        korrigerer?.let { k ->
            val soknadFraDb = sykepengesoknadRepository.findBySykepengesoknadUuid(k)
            if (soknadFraDb == null) {
                if (environmentToggles.isProduction()) {
                    throw RuntimeException("Forventer å finne søknad med id $k")
                }
                return
            }

            if (soknadFraDb.status != KORRIGERT) {
                if (soknadFraDb.status != SENDT) {
                    throw RuntimeException("Forventet at ${soknadFraDb.id} har status sendt")
                }
                sykepengesoknadRepository.save(
                    soknadFraDb.copy(
                        status = KORRIGERT,
                        korrigertAv = this.id
                    )
                )
                return
            }
            if (soknadFraDb.korrigertAv != this.id) {
                throw RuntimeException("Forventet at ${soknadFraDb.id} var korrigert av $id")
            }
        }
    }
}

fun String.tilSykepengesoknadDto(): SykepengesoknadDTO = OBJECT_MAPPER.readValue(this)
