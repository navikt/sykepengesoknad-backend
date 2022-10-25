package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.personhendelseTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.tilOsloZone
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val ANNULLERT = "ANNULLERT"
private const val OPPHOERT = "OPPHOERT"
private const val OPPLYSNINGSTYPE_DODSFALL = "DOEDSFALL_V1"

@Component
class AivenDodsfallConsumer(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val identService: IdentService,
) : ConsumerSeekAware {

    val log = logger()

    @KafkaListener(
        topics = [personhendelseTopic],
        id = "sykepengesoknad-personhendelse-test-3",
        idIsGroup = true,
        containerFactory = "kafkaAvroListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listenAiven(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        log.info("Mottok personhendelse på aiven ${cr.key()} ${cr.value()}")

        metrikk.personHendelseAiven()

        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: MutableMap<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback
    ) {
        assignments.forEach {
            callback.seekRelative(it.key.topic(), it.key.partition(), -1, false)
        }
    }

    fun prosesserPersonhendelse(personhendelse: GenericRecord, timestamp: Long) {
        metrikk.personHendelseMottatt()

        if (personhendelse.erDodsfall()) {
            metrikk.dodsfallMottatt()

            val fnr = personhendelse.hentFnr()
            val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)

            if (harUutfylteSoknader(identer)) {
                when (personhendelse.hentEndringstype()) {
                    OPPRETTET, KORRIGERT -> {
                        val dodsdato = personhendelse.hentDodsdato()
                        if (dodsmeldingDAO.harDodsmelding(identer)) {
                            log.info("Oppdaterer dodsdato")
                            dodsmeldingDAO.oppdaterDodsdato(identer, dodsdato)
                        } else {
                            log.info("Lagrer ny dodsmelding")
                            dodsmeldingDAO.lagreDodsmelding(identer, dodsdato, Instant.ofEpochMilli(timestamp).tilOsloZone())
                        }
                    }
                    ANNULLERT, OPPHOERT -> {
                        log.info("Sletter dodsmelding")
                        dodsmeldingDAO.slettDodsmelding(identer)
                    }
                }
            }
        } else {
            log.debug("Ignorerer personhendelse med type ${personhendelse.hentOpplysningstype()}")
        }
    }

    private fun GenericRecord.hentOpplysningstype() =
        get("opplysningstype").toString()

    private fun GenericRecord.erDodsfall() =
        hentOpplysningstype() == OPPLYSNINGSTYPE_DODSFALL

    private fun GenericRecord.hentFnr() =
        (get("personidenter") as GenericData.Array<*>)
            .map { it.toString() }
            .first { it.length == 11 }

    private fun GenericRecord.hentEndringstype() =
        get("endringstype").toString()

    private fun GenericRecord.hentDodsdato(): LocalDate {
        try {
            return LocalDate.ofEpochDay((get("doedsfall") as GenericRecord?)?.get("doedsdato").toString().toLong())
        } catch (exception: Exception) {
            logger().error("Deserialisering av dødsdato feiler")
            throw exception
        }
    }

    private fun harUutfylteSoknader(identer: FolkeregisterIdenter) =
        sykepengesoknadDAO.finnSykepengesoknader(identer)
            .any { listOf(Soknadstatus.NY, Soknadstatus.FREMTIDIG).contains(it.status) }
}
