package no.nav.helse.flex.client.sykmeldinger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import org.springframework.stereotype.Component

interface FlexSykmeldingerClient {
    fun hentSykmeldinger(
        sykmeldingIder: Set<String>,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<SykmeldingKafkaMessage>
}

@Component
class FlexSykmeldingerClientEkstern : FlexSykmeldingerClient {
    override fun hentSykmeldinger(
        sykmeldingIder: Set<String>,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<SykmeldingKafkaMessage> {
        TODO("Not yet implemented")
    }
}
