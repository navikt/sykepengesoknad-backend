package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykmeldingmerknader.OppdateringAvMerknader
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service

@Service
class BehandleSykmeldingOgBestillAktivering(
    private val behandleSendtBekreftetSykmelding: BehandleSendtBekreftetSykmelding,
    private val aktiveringProducer: AktiveringProducer,
    private val oppdateringAvMerknader: OppdateringAvMerknader,
) {
    val log = logger()

    fun prosesserSykmelding(
        sykmeldingId: String,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO?,
        topic: String,
    ) {
        val skalAktiveres =
            behandleSendtBekreftetSykmelding.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, topic)

        sykmeldingKafkaMessage?.let {
            oppdateringAvMerknader.oppdaterMerknader(it)
        }

        return skalAktiveres
            .forEach { aktiveringProducer.leggPaAktiveringTopic(it) }
    }
}
