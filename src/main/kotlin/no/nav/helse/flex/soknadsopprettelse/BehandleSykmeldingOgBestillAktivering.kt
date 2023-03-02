package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service

@Service
class BehandleSykmeldingOgBestillAktivering(
    private val behandleSendtBekreftetSykmelding: BehandleSendtBekreftetSykmelding,
    private val aktiveringProducer: AktiveringProducer

) {
    val log = logger()

    fun prosesserSykmelding(sykmeldingId: String, sykmeldingKafkaMessage: SykmeldingKafkaMessage?, topic: String) {
        val prosesserSykmelding =
            behandleSendtBekreftetSykmelding.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, topic)
        return prosesserSykmelding
            .forEach { aktiveringProducer.leggPaAktiveringTopic(it) }
    }
}
