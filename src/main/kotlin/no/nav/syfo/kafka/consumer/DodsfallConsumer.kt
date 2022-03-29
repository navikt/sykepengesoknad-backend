package no.nav.syfo.kafka.consumer

import no.nav.syfo.domain.Soknadstatus.FREMTIDIG
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.kafka.getSafeNavCallIdHeaderAsString
import no.nav.syfo.logger
import no.nav.syfo.repository.DodsmeldingDAO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.service.IdentService
import no.nav.syfo.util.Metrikk
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val ANNULLERT = "ANNULLERT"
private const val OPPHOERT = "OPPHOERT"
private const val OPPLYSNINGSTYPE_DODSFALL = "DOEDSFALL_V1"

@Component
class DodsfallConsumer(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val identService: IdentService,
) {

    @KafkaListener(
        topics = ["aapen-person-pdl-leesah-v1"],
        id = "personhendelse",
        idIsGroup = false,
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        MDC.put(NAV_CALLID, getSafeNavCallIdHeaderAsString(cr.headers()))

        val personhendelse = cr.value()
        metrikk.personHendelseMottatt()
        try {
            val aktorId = personhendelse.hentAktorId()

            if (personhendelse.erDodsfall()) {
                metrikk.dodsfallMottatt()
                val identer = identService.hentFolkeregisterIdenterMedHistorikkForAktorid(aktorId)
                if (harUutfylteSoknader(identer)) {
                    when (personhendelse.hentEndringstype()) {
                        OPPRETTET, KORRIGERT -> {
                            val dodsdato = personhendelse.hentDodsdato()
                            if (dodsmeldingDAO.harDodsmelding(aktorId)) {
                                logger().info("Oppdaterer dodsdato for $aktorId")
                                dodsmeldingDAO.oppdaterDodsdato(aktorId, dodsdato)
                            } else {
                                logger().info("Lagrer ny dodsmelding for $aktorId")
                                dodsmeldingDAO.lagreDodsmelding(aktorId, dodsdato)
                            }
                        }
                        ANNULLERT, OPPHOERT -> {
                            logger().info("Sletter dodsmelding for $aktorId")
                            dodsmeldingDAO.slettDodsmelding(aktorId)
                        }
                    }
                }
            } else {
                logger().debug("Ignorerer personhendelse med type ${personhendelse.hentOpplysningstype()}")
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            // Vi behøver muligens å logge mer her, siden KafkaErrorHandler ikke logger meldingsverdi
            throw RuntimeException("Uventet feil ved behandling av personhendelse", e)
        } finally {
            MDC.remove(NAV_CALLID)
        }
    }

    private fun GenericRecord.hentOpplysningstype() =
        get("opplysningstype").toString()

    private fun GenericRecord.erDodsfall() =
        hentOpplysningstype() == OPPLYSNINGSTYPE_DODSFALL

    private fun GenericRecord.hentAktorId() =
        (get("personidenter") as GenericData.Array<*>)
            .map { it.toString() }
            .first { it.length == 13 }

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

    private fun harUutfylteSoknader(identer: List<String>) =
        sykepengesoknadDAO.finnSykepengesoknader(identer)
            .any { listOf(NY, FREMTIDIG).contains(it.status) }
}
