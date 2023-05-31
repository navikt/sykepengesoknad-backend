package no.nav.helse.flex.client.medlemskap

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.util.*

const val MEDLEMSKAP_VURDERING_PATH = "brukersporsmal"

@Component
class MedlemskapVurderingClient(
    private val medlemskapVurderingRestTemplate: RestTemplate,
    @Value("\${MEDLEMSKAP_VURDERING_URL}")
    private val url: String
) {
    val log = logger()

    fun hentMedlemskapVurdering(medlemskapVurderingRequest: MedlemskapVurderingRequest): MedlemskapVurderingResponse {
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.set("fnr", medlemskapVurderingRequest.fnr)

        val queryBuilder = UriComponentsBuilder
            .fromHttpUrl(url)
            .pathSegment(MEDLEMSKAP_VURDERING_PATH)
            .queryParam("fom", medlemskapVurderingRequest.fom)
            .queryParam("tom", medlemskapVurderingRequest.tom)

        // TODO: Implementer feilhåndtering og lagring til database.
        val response = try {
            medlemskapVurderingRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    HttpMethod.GET,
                    HttpEntity(null, headers),
                    MedlemskapVurderingResponse::class.java
                )
        } catch (e: Exception) {
            TODO("Not yet implemented")
        }

        return response.body!!
    }
}

data class MedlemskapVurderingRequest(
    var fnr: String,
    var fom: LocalDate,
    var tom: LocalDate
)

data class MedlemskapVurderingResponse(
    var svar: MedlemskapVurderingSvarType,
    val sporsmal: List<MedlemskapVurderingSporsmal>
)

enum class MedlemskapVurderingSvarType {
    JA, NEI, UAVKLART
}

enum class MedlemskapVurderingSporsmal {
    OPPHOLDSTILATELSE, ARBEID_UTENFOR_NORGE, OPPHOLD_UTENFOR_EØS_OMRÅDE, OPPHOLD_UTENFOR_NORGE
}
