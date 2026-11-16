package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.aktivering.AktiveringBestiller
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykmeldingmerknader.OppdateringAvMerknader
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service

@Service
class BehandleSykmeldingOgBestillAktivering(
    private val behandleSendtBekreftetSykmelding: BehandleSendtBekreftetSykmelding,
    private val aktiveringBestiller: AktiveringBestiller,
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
            .forEach { aktiveringBestiller.bestillAktivering(it) }
    }

    fun prosesserSykmeldingMedOptIn(sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO) {
        val skalAktiveres =
            behandleSendtBekreftetSykmelding.opprettSoknadNaringsdrivende(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                arbeidssituasjon = sykmeldingKafkaMessage.hentArbeidssituasjon()!!,
                optIn = true,
            )

        skalAktiveres.forEach { aktiveringBestiller.bestillAktivering(it) }
    }
}
