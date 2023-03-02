package no.nav.helse.flex.client.kvitteringer

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SykepengesoknadKvitteringerClient(
    @Value("\${sykepengesoknad.kvitteringer.url}")
    private val sykepengesoknadKvitteringerUrl: String,
    private val sykepengesoknadKvitteringerRestTemplate: RestTemplate
) {

    fun slettKvittering(blobName: String) {
        sykepengesoknadKvitteringerRestTemplate.delete("$sykepengesoknadKvitteringerUrl/api/v2/kvittering/$blobName")
    }
}
