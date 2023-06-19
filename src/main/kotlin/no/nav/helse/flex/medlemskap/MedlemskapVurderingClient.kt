package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.system.measureTimeMillis

const val MEDLEMSKAP_VURDERING_PATH = "brukersporsmal"

@Component
class MedlemskapVurderingClient(
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
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

        val response: ResponseEntity<MedlemskapVurderingResponse>
        val svarTid = try {
            measureTimeMillis {
                response = medlemskapVurderingRestTemplate
                    .exchange(
                        queryBuilder.toUriString(),
                        HttpMethod.GET,
                        HttpEntity(null, headers),
                        MedlemskapVurderingResponse::class.java
                    )
            }
        } catch (e: Exception) {
            throw MedlemskapVurderingClientException("Feil ved kall til MedlemskapVurdering.", e)
        }

        val medlemskapVurderingResponse = response.body!!

        lagreVurdering(
            medlemskapVurderingRequest,
            medlemskapVurderingResponse,
            svarTid
        )

        // Mottar vi svar.UAVKLART skal vi også motta spørsmål å stille brukeren.
        if (medlemskapVurderingResponse.svar == MedlemskapVurderingSvarType.UAVKLART && medlemskapVurderingResponse.sporsmal.isEmpty()) {
            throw MedlemskapVurderingResponseException("MedlemskapVurdering returnerte svar.UAVKLART uten spørsmål.")
        }

        // Mottar vi en avklart situasjon (svar.JA eller svar.NEI) trenger vi ikke spørsmnål å stille brukeren.
        if (listOf(
                MedlemskapVurderingSvarType.JA,
                MedlemskapVurderingSvarType.NEI
            ).contains(medlemskapVurderingResponse.svar) && medlemskapVurderingResponse.sporsmal.isNotEmpty()
        ) {
            throw MedlemskapVurderingResponseException(
                "MedlemskapVurdering returnerte spørsmål selv om svar var " +
                    "svar.${medlemskapVurderingResponse.svar}."
            )
        }
        return response.body!!
    }

    private fun lagreVurdering(
        medlemskapVurderingRequest: MedlemskapVurderingRequest,
        medlemskapVurderingResponse: MedlemskapVurderingResponse,
        svarTid: Long
    ) {
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = Instant.now(),
                svartid = svarTid,
                fnr = medlemskapVurderingRequest.fnr,
                fom = medlemskapVurderingRequest.fom,
                tom = medlemskapVurderingRequest.tom,
                svartype = medlemskapVurderingResponse.svar.toString(),
                sporsmal = medlemskapVurderingResponse.sporsmal.tilPostgresJson(),
                sykepengesoknadId = medlemskapVurderingRequest.sykepengesoknadId
            )
        )
    }
}

/**
 * Kastes når det oppstår en teknisk feil ved kall til LovMe.
 */
class MedlemskapVurderingClientException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

/**
 * Kastes når LovMe returnerer en feilmelding / statuskode som ikke er 2xx som følge av en logisk feil.
 */
class MedlemskapVurderingResponseException : RuntimeException {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

data class MedlemskapVurderingRequest(
    var fnr: String,
    var fom: LocalDate,
    var tom: LocalDate,
    var sykepengesoknadId: String
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
