package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("fakes")
@Primary
class FlexSykmeldingerBackendClientFake : FlexSykmeldingerBackendClient {
    private val sykmeldinger = mutableListOf<SykmeldingKafkaMessageDTO>()

    override fun hentSykmeldinger(
        sykmeldingIder: Set<String>,
        fom: LocalDate?,
    ): List<SykmeldingKafkaMessageDTO> =
        sykmeldinger
            .filter { it.sykmelding.id in sykmeldingIder }
            .filter { if (fom != null) it.sykmelding.tom!! >= fom else true }

    fun leggTilSykmelding(sykmelding: SykmeldingKafkaMessageDTO) {
        sykmeldinger.add(sykmelding)
    }

    fun reset() {
        sykmeldinger.clear()
    }
}
