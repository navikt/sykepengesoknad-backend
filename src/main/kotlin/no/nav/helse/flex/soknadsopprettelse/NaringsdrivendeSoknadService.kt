package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.stereotype.Component

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    fun finnSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessage> {
        val sykmeldingIder = flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
        val sykmeldingerSomManglerSoknad =
            sykmeldingIder - setOf(sykmeldingKafkaMessage.sykmelding.id) -
                sykepengesoknadRepository
                    .findBySykmeldingUuidIn(sykmeldingIder)
                    .map { it.sykmeldingUuid!! }
                    .toSet()

        if (sykmeldingerSomManglerSoknad.none { it == sykmeldingKafkaMessage.sykmelding.id }) {
            throw RuntimeException("Sykmeldingen ${sykmeldingKafkaMessage.sykmelding.id} er i listen over sykmeldinger som mangler søknad: $sykmeldingerSomManglerSoknad")
        }

        return if (sykmeldingerSomManglerSoknad.isEmpty()) {
            emptyList()
        } else {
            return flexSykmeldingerBackendClient
                .hentSykmeldinger(sykmeldingIder = sykmeldingerSomManglerSoknad)
                .filter { it.hentArbeidssituasjon() == arbeidssituasjon }
        }
    }
}
