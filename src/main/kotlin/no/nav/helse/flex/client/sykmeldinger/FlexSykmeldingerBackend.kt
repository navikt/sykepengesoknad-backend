package no.nav.helse.flex.client.sykmeldinger

import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import org.springframework.stereotype.Component

interface FlexSykmeldingerClient {
    fun hentSykmeldinger(): List<SykmeldingKafkaMessage>
}

@Component
class FlexSykmeldingerClientEkstern : FlexSykmeldingerClient {
    override fun hentSykmeldinger(): List<SykmeldingKafkaMessage> {
        TODO("Not yet implemented")
    }
}
