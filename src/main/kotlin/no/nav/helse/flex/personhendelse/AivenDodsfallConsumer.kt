package no.nav.helse.flex.personhendelse

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.personhendelseTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.tilOsloZone
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AivenDodsfallConsumer(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val identService: IdentService,
) {

    val log = logger()

    @KafkaListener(
        topics = [personhendelseTopic],
        id = "sykepengesoknad-personhendelse",
        idIsGroup = true,
        containerFactory = "kafkaAvroListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listenAiven(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        log.info("Mottok personhendelse på aiven")

        metrikk.personHendelseAiven()

        prosesserPersonhendelse(
            cr.value() as Personhendelse,
            cr.timestamp(),
        )

        acknowledgment.acknowledge()
    }

    fun prosesserPersonhendelse(personhendelse: Personhendelse, timestamp: Long) {
        metrikk.personHendelseMottatt()

        if (personhendelse.erDodsfall) {
            metrikk.dodsfallMottatt()

            val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(personhendelse.fnr)

            if (harUutfylteSoknader(identer)) {
                when (personhendelse.endringstype) {
                    OPPRETTET, KORRIGERT -> {
                        val dodsdato = personhendelse.dodsdato

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
            log.debug("Ignorerer personhendelse med type ${personhendelse.opplysningstype}")
        }
    }

    private fun harUutfylteSoknader(identer: FolkeregisterIdenter) =
        sykepengesoknadDAO.finnSykepengesoknader(identer)
            .any { listOf(Soknadstatus.NY, Soknadstatus.FREMTIDIG).contains(it.status) }
}
