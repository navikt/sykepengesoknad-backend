package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.exception.UgyldigOptInSykmeldingException
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.helse.flex.soknadsopprettelse.hentArbeidssituasjon
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class OptInService(
    private val behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering,
) {
    private val log = logger()

    fun opprettOptInnSoknad(sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO) {
        sykmeldingKafkaMessage.run {
            validerStatusForOptIn()
            validerArbeidssituasjonForOptIn()
            validerAlderForOptIn()
        }

        behandleSykmeldingOgBestillAktivering.prosesserSykmeldingMedOptIn(sykmeldingKafkaMessage)
        log.info("Prosesserte opt-in sykmelding ${sykmeldingKafkaMessage.sykmelding.id} ")
    }

    private fun SykmeldingKafkaMessageDTO.validerStatusForOptIn() {
        if (event.statusEvent != STATUS_BEKREFTET) {
            throw UgyldigOptInSykmeldingException(
                "Sykmelding ${sykmelding.id} har ugyldig statusEvent ${event.statusEvent}, forventet $STATUS_BEKREFTET",
            )
        }
    }

    private fun SykmeldingKafkaMessageDTO.validerArbeidssituasjonForOptIn() {
        val arbeidssituasjon =
            hentArbeidssituasjon()
                ?: throw UgyldigOptInSykmeldingException("Fant ikke arbeidssituasjon for sykmelding ${sykmelding.id}")

        if (arbeidssituasjon !in setOf(Arbeidssituasjon.FRILANSER, Arbeidssituasjon.NAERINGSDRIVENDE)) {
            throw UgyldigOptInSykmeldingException(
                "Ugyldig arbeidssituasjon $arbeidssituasjon for sykmelding ${sykmelding.id}",
            )
        }
    }

    private fun SykmeldingKafkaMessageDTO.validerAlderForOptIn() {
        if (sykmelding.mottattTidspunkt.isBefore(OffsetDateTime.now().minusMonths(4).minusDays(1))) {
            throw UgyldigOptInSykmeldingException("Sykmelding ${sykmelding.id} er for gammel for opt-in")
        }
    }
}
