package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.unleash.UnleashToggles
import org.springframework.stereotype.Component

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val unleashToggles: UnleashToggles,
) {
    private val log = logger()

    fun finnAndreSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessage> {
        val skalOppretteVentetidsoknader = unleashToggles.opprettVentetidsoknaderEnabled(identer.originalIdent)
        return try {
            val sykmeldingIder =
                flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
            log.info("Fant ${sykmeldingIder.size} sykmeldinger med samme ventetid ${sykmeldingKafkaMessage.sykmelding.id}: $sykmeldingIder")

            val andreSykmeldingIder = sykmeldingIder.filterNot { it == sykmeldingKafkaMessage.sykmelding.id }.toSet()

            val andreSykmeldingIderMedSoknader =
                sykepengesoknadRepository
                    .findBySykmeldingUuidIn(andreSykmeldingIder)
                    .map { it.sykmeldingUuid!! }
                    .toSet()

            val andreSykmeldingerSomManglerSoknad = andreSykmeldingIder - andreSykmeldingIderMedSoknader

            val andreSykmeldingerMedSammeArbeidsforhold =
                if (andreSykmeldingerSomManglerSoknad.isEmpty()) {
                    emptyList()
                } else {
                    flexSykmeldingerBackendClient
                        .hentSykmeldinger(sykmeldingIder = andreSykmeldingerSomManglerSoknad)
                        .filter { it.hentArbeidssituasjon() == arbeidssituasjon }
                }

            if (skalOppretteVentetidsoknader) {
                log.info(
                    "(Toggle På) Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} " +
                        "andre sykmeldinger som skal ha søknad med sykmelding: ${sykmeldingKafkaMessage.sykmelding.id}: " +
                        "${andreSykmeldingerMedSammeArbeidsforhold.map { it.sykmelding.id }}",
                )
                andreSykmeldingerMedSammeArbeidsforhold
            } else {
                log.info(
                    "(Toggle Av) Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} " +
                        "andre sykmeldinger som skal ha søknad med sykmelding: ${sykmeldingKafkaMessage.sykmelding.id}: " +
                        "${andreSykmeldingerMedSammeArbeidsforhold.map { it.sykmelding.id }}",
                )
                emptyList()
            }
        } catch (e: Exception) {
            if (skalOppretteVentetidsoknader) {
                throw e
            } else {
                log.warn("Feil ved henting av sykmeldinger med samme ventetid", e)
                emptyList()
            }
        }
    }
}
