package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.exception.IkkeTilgangException
import no.nav.helse.flex.exception.UgyldigOptInSykmeldingException
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.helse.flex.soknadsopprettelse.hentArbeidssituasjon
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class OptInService(
    private val behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
) {
    private val log = logger()

    fun harSoknadForSykmelding(
        sykmeldingUuid: String,
        identer: FolkeregisterIdenter,
        arbeidssituasjon: Arbeidssituasjon,
    ): Boolean {
        validerArbeidssituasjon(sykmeldingUuid, arbeidssituasjon)

        val soknader =
            sykepengesoknadRepository
                .findBySykmeldingUuid(sykmeldingUuid)
                .filter { it.arbeidssituasjon == arbeidssituasjon }

        if (soknader.isNotEmpty()) {
            if (soknader.any { it.fnr !in identer.alle() }) {
                throw IkkeTilgangException("Er ikke eier")
            }

            log.info(
                "HarSoknad: Fant ${soknader.size} søknader for sykmelding $sykmeldingUuid (${arbeidssituasjon.name}): " +
                    "${soknader.map { it.id }}",
            )
            return true
        }

        return if (arbeidssituasjon == Arbeidssituasjon.NAERINGSDRIVENDE) {
            klippetSykepengesoknadRepository
                .existsBySykmeldingUuid(sykmeldingUuid)
                .also { log.info("HarSoknad: Fant klipp av $sykmeldingUuid (${arbeidssituasjon.name}): $it") }
        } else {
            log.info("HarSoknad: Fant ingen søknader for sykmelding $sykmeldingUuid (${arbeidssituasjon.name})")
            false
        }
    }

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

        validerArbeidssituasjon(sykmelding.id, arbeidssituasjon)
    }

    private fun validerArbeidssituasjon(
        sykmeldingUuid: String,
        arbeidssituasjon: Arbeidssituasjon,
    ) {
        if (arbeidssituasjon !in setOf(Arbeidssituasjon.FRILANSER, Arbeidssituasjon.NAERINGSDRIVENDE)) {
            throw UgyldigOptInSykmeldingException(
                "Ugyldig arbeidssituasjon ${arbeidssituasjon.name} for sykmelding $sykmeldingUuid",
            )
        }
    }

    private fun SykmeldingKafkaMessageDTO.validerAlderForOptIn() {
        if (sykmelding.mottattTidspunkt.isBefore(OffsetDateTime.now().minusMonths(4).minusDays(1))) {
            throw UgyldigOptInSykmeldingException("Sykmelding ${sykmelding.id} er for gammel for opt-in")
        }
    }
}
