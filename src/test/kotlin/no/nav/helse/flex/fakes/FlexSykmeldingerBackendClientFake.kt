package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("fakes")
@Primary
class FlexSykmeldingerBackendClientFake : FlexSykmeldingerBackendClient {
    private val sykmeldinger = mutableListOf<SykmeldingKafkaMessageDTO>()

    override fun hentSykmeldinger(sykmeldingIder: Set<String>): List<SykmeldingKafkaMessageDTO> =
        sykmeldinger.filter {
            it.sykmelding.id in
                sykmeldingIder
        }

    fun leggTilSykmelding(sykmelding: SykmeldingKafkaMessageDTO) {
        sykmeldinger.add(sykmelding)
    }

    fun reset() {
        sykmeldinger.clear()
    }
}
