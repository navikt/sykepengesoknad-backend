package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.tilOsloZone
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.kafka.getSafeNavCallIdHeaderAsString
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
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
class DodsfallConsumer(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val identService: IdentService,
) {

    val log = logger()

    @KafkaListener(
        topics = ["aapen-person-pdl-leesah-v1"],
        id = "sykepengesoknad-personhendelse",
        idIsGroup = true,
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        MDC.put(NAV_CALLID, getSafeNavCallIdHeaderAsString(cr.headers()))

        val personhendelse = cr.value()
        metrikk.personHendelseMottatt()
        try {
            val fnr = personhendelse.hentFnr()

            if (personhendelse.erDodsfall()) {
                metrikk.dodsfallMottatt()
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
                                dodsmeldingDAO.lagreDodsmelding(identer, dodsdato, Instant.ofEpochMilli(cr.timestamp()).tilOsloZone())
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
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            throw RuntimeException("Uventet feil ved behandling av personhendelse", e)
        } finally {
            MDC.remove(NAV_CALLID)
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
            logger().error("Deserialisering av d??dsdato feiler")
            throw exception
        }
    }

    private fun harUutfylteSoknader(identer: FolkeregisterIdenter) =
        sykepengesoknadDAO.finnSykepengesoknader(identer)
            .any { listOf(NY, FREMTIDIG).contains(it.status) }
}
