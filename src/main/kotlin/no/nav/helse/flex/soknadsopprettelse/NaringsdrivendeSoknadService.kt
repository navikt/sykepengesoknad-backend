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

    fun finnSykmeldingerSomManglerSoknad(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): List<SykmeldingKafkaMessage> {
        val sykmeldingIder = flexSyketilfelleClient.hentSykmeldingerMedSammeVentetid(sykmeldingKafkaMessage, identer)
        log.info("Fant ${sykmeldingIder.size} sykmeldinger med samme ventetid ${sykmeldingKafkaMessage.sykmelding.id}: $sykmeldingIder")

        val sykmeldingIderUtenomDenne = sykmeldingIder.filterNot { it == sykmeldingKafkaMessage.sykmelding.id }.toSet()

        val sykmeldingIderMedSoknader =
            sykepengesoknadRepository
                .findBySykmeldingUuidIn(sykmeldingIderUtenomDenne)
                .map { it.sykmeldingUuid!! }
                .toSet()

        val sykmeldingerSomManglerSoknad = sykmeldingIderUtenomDenne - sykmeldingIderMedSoknader

        return if (sykmeldingerSomManglerSoknad.isEmpty()) {
            log.info("Oppretter næringsdrivende søknad for sykmelding ${sykmeldingKafkaMessage.sykmelding.id}")
            emptyList()
        } else {
            log.info(
                "Oppretter næringsdrivende søknader for ${sykmeldingerSomManglerSoknad.size + 1} sykmeldinger ${sykmeldingKafkaMessage.sykmelding.id}: $sykmeldingerSomManglerSoknad",
            )
            flexSykmeldingerBackendClient
                .hentSykmeldinger(sykmeldingIder = sykmeldingerSomManglerSoknad)
                .filter { it.hentArbeidssituasjon() == arbeidssituasjon }
        }
    }
}
