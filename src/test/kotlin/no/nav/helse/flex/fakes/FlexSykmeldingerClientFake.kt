package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Profile

@TestComponent
@Profile("test")
class FlexSykmeldingerClientFake : FlexSykmeldingerClient {
    private val sykmeldinger = mutableListOf<SykmeldingKafkaMessage>()

    override fun hentSykmeldinger(
        sykmeldingIder: Set<String>,
        arbeidssituasjon: Arbeidssituasjon,
    ): List<SykmeldingKafkaMessage> = sykmeldinger

    fun leggTilSykmelding(sykmelding: SykmeldingKafkaMessage) {
        sykmeldinger.add(sykmelding)
    }

    fun reset() {
        sykmeldinger.clear()
    }
}
