package no.nav.helse.flex.fakes

import no.nav.helse.flex.client.sykmeldinger.FlexSykmeldingerBackendClient
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage

class FlexSykmeldingerBackendClientFake : FlexSykmeldingerBackendClient {
    private val sykmeldinger = mutableListOf<SykmeldingKafkaMessage>()

    override fun hentSykmeldinger(sykmeldingIder: Set<String>): List<SykmeldingKafkaMessage> = sykmeldinger

    fun leggTilSykmelding(sykmelding: SykmeldingKafkaMessage) {
        sykmeldinger.add(sykmelding)
    }

    fun reset() {
        sykmeldinger.clear()
    }
}
