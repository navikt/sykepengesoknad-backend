package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.AvbrytSoknadService
import no.nav.helse.flex.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class GjenskapSoknadConsumer(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService,
    private val avbrytSoknadService: AvbrytSoknadService,
) {

    private val log = logger()
    private val sykmeldingId = "5aec579e-2c82-4a69-aaab-cb251b287dfb"

    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "gjenskap-soknad",
        idIsGroup = true,
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        if (cr.key() == sykmeldingId) {
            val melding = cr.value()?.tilSykmeldingKafkaMessage()

            if (melding?.event?.statusEvent != STATUS_SENDT) return

            gjenskapSoknad(
                cr.key(),
                melding,
            )
        }

        acknowledgment.acknowledge()
    }

    fun gjenskapSoknad(
        id: String,
        melding: SykmeldingKafkaMessage,
    ) {
        val klippetSoknad = sykepengesoknadDAO
            .finnSykepengesoknaderForSykmelding(id)
            .runCatching {
                require(size == 1)
                require(first().sykmeldingId == sykmeldingId)
                require(first().status == Soknadstatus.NY)
                first()
            }.onFailure {
                log.info("Fant ikke søknad for sykmelding $id")
                return
            }.onSuccess {
                log.info("Hentet soknad ${it.id} med fom ${it.fom} og tom ${it.tom}")
            }.getOrNull()!!

        avbrytSoknadService.avbrytSoknad(klippetSoknad)

        sykepengesoknadDAO.slettSoknad(klippetSoknad.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(id, melding)
    }
}
