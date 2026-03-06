package no.nav.helse.flex.client.sykmeldinger

import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

interface FlexSykmeldingerBackendClient {
    fun hentSykmeldinger(sykmeldingIder: Set<String>): List<SykmeldingKafkaMessage>
}

data class SykmeldingerRequest(
    val sykmeldingIder: List<String>,
)

data class SykmeldingerResponse(
    val sykmeldinger: List<SykmeldingKafkaMessage>,
)

@Component
class FlexSykmeldingerBackendClientEkstern(
    private val flexSykmeldingerBackendRestClient: RestClient,
) : FlexSykmeldingerBackendClient {
    override fun hentSykmeldinger(sykmeldingIder: Set<String>): List<SykmeldingKafkaMessage> =
        flexSykmeldingerBackendRestClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/sykmeldinger/kafka")
                    .build()
            }.contentType(APPLICATION_JSON)
            .body(SykmeldingerRequest(sykmeldingIder.toList()))
            .retrieve()
            .toEntity<SykmeldingerResponse>()
            .body
            ?.sykmeldinger ?: throw RuntimeException("Hent sykmeldinger feilet, tom body i responsen")
}
