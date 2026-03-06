package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.stereotype.Component

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    fun finnAndreSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessage> {
        val sykmeldingIder = flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
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

        log.info(
            "Fant ${andreSykmeldingerMedSammeArbeidsforhold.size} " +
                "andre sykmeldinger som skal ha søknad med sykmelding: ${sykmeldingKafkaMessage.sykmelding.id}: " +
                "${andreSykmeldingerMedSammeArbeidsforhold.map { it.sykmelding.id }}",
        )
        return andreSykmeldingerMedSammeArbeidsforhold
    }
}
