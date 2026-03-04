package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.stereotype.Component

@Component
class NaringsdrivendeSoknadService(
    private val flexSyketilfelleClient: FlexSyketilfelleClient,
    private val flexSykmeldingerBackendClient: FlexSykmeldingerBackendClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    fun finnSykmeldingerSomManglerSoknad(
        sykmeldingId: String,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<SykmeldingKafkaMessage> {
        val sykmeldingIder = flexSyketilfelleClient.hentSykmeldingerIsykeforloep(sykmeldingId)
        val sykmeldingerSomManglerSoknad =
            sykmeldingIder - setOf(sykmeldingId) -
                sykepengesoknadRepository
                    .findBySykmeldingUuidIn(sykmeldingIder)
                    .map { it.sykmeldingUuid!! }
                    .toSet()
        return if (sykmeldingerSomManglerSoknad.isEmpty()) {
            emptyList()
        } else {
            return flexSykmeldingerBackendClient
                .hentSykmeldinger(sykmeldingIder = sykmeldingerSomManglerSoknad)
                .filter { it.hentArbeidssituasjon() == arbeidssituasjon }
        }
    }
}
