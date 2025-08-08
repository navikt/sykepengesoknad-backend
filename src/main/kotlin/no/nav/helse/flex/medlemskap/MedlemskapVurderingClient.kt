package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.time.LocalDate
import kotlin.system.measureTimeMillis

const val MEDLEMSKAP_VURDERING_PATH = "brukersporsmal"

@Component
class MedlemskapVurderingClient(
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
    private val medlemskapVurderingRestTemplate: RestTemplate,
    @Value("\${MEDLEMSKAP_VURDERING_URL}")
    private val url: String,
) {
    val log = logger()

    /**
     * Henter medlemskapvurdering fra LovMe og lagrer den i database. Svar fra LovMe blir lagret selv om det kastes
     * exception på grunn av logiske feil.
     *
     * @return MedlemskapVurderingResponse hentet fra Lovme.
     * @throws MedlemskapVurderingClientException ved tekniske feil.
     * @throws MedlemskapVurderingResponseException ved logisk feil, som f.eks. at det returneres spørsmål når det ikke forventes.
     */
    fun hentMedlemskapVurdering(medlemskapVurderingRequest: MedlemskapVurderingRequest): MedlemskapVurderingResponse {
        val headers = HttpHeaders()
        val soknadId = medlemskapVurderingRequest.sykepengesoknadId
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.set("fnr", medlemskapVurderingRequest.fnr)
        headers.set("Nav-Call-Id", soknadId)

        val queryBuilder =
            UriComponentsBuilder
                .fromUriString(url)
                .pathSegment(MEDLEMSKAP_VURDERING_PATH)
                .queryParam("fom", medlemskapVurderingRequest.fom)
                .queryParam("tom", medlemskapVurderingRequest.tom)

        val response: ResponseEntity<MedlemskapVurderingResponse>
        val svarTid =
            try {
                measureTimeMillis {
                    response =
                        medlemskapVurderingRestTemplate
                            .exchange(
                                queryBuilder.toUriString(),
                                HttpMethod.GET,
                                HttpEntity(null, headers),
                                MedlemskapVurderingResponse::class.java,
                            )
                }
            } catch (e: Exception) {
                throw MedlemskapVurderingClientException(
                    "MedlemskapVurdering med Nav-Call-Id: $soknadId feilet.",
                    e,
                )
            }

        val medlemskapVurderingResponse = response.body!!

        lagreMedlemskapVurdering(
            medlemskapVurderingRequest,
            medlemskapVurderingResponse,
            svarTid,
        )

        // Periode for kjent oppholdstillatelse skal kun returneres når spørsmål om oppholdstillatelse stilles.
        if (medlemskapVurderingResponse.kjentOppholdstillatelse != null &&
            !medlemskapVurderingResponse.sporsmal.contains(MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE)
        ) {
            throw MedlemskapVurderingResponseException(
                "MedlemskapVurdering med Nav-Call-Id: $soknadId returnerte kjentOppholdstillatelse når spørsmål " +
                    "${MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE} mangler.",
            )
        }

        // Periode for kjent oppholdstillatelse skal alltid returneres når spørsmål om oppholdstillatelse stilles.
        if (medlemskapVurderingResponse.kjentOppholdstillatelse == null &&
            medlemskapVurderingResponse.sporsmal.contains(MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE)
        ) {
            val idListe =
                listOf(
                    "f5d45c8f-879c-3fb4-9d4f-afc455e71345",
                    "b9b435b8-8c58-3595-980b-63a6d7c2465f",
                )
            if (idListe.contains(medlemskapVurderingRequest.sykepengesoknadId)) {
                log.info(
                    "Fjerner medlemskapsspørsmål om ${MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE} for " +
                        "søknad: ${medlemskapVurderingRequest.sykepengesoknadId} da spørsmålet mangler kjentOppholdstillatelse.",
                )
                val filtrertListe =
                    medlemskapVurderingResponse.sporsmal.filter { it != MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE }
                return medlemskapVurderingResponse.copy(sporsmal = filtrertListe)
            } else {
                throw MedlemskapVurderingResponseException(
                    "MedlemskapVurdering med Nav-Call-Id: $soknadId mangler kjentOppholdstillatelse når spørsmål " +
                        "${MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE} stilles.",
                )
            }
        }

        // Mottar vi en avklart situasjon (svar.JA eller svar.NEI) skal vi ikke få spørsmål å stille brukeren.
        if (listOf(
                MedlemskapVurderingSvarType.JA,
                MedlemskapVurderingSvarType.NEI,
            ).contains(medlemskapVurderingResponse.svar) &&
            medlemskapVurderingResponse.sporsmal.isNotEmpty()
        ) {
            throw MedlemskapVurderingResponseException(
                "MedlemskapVurdering med Nav-Call-Id: $soknadId returnerte spørsmål selv om svar var " +
                    "svar.${medlemskapVurderingResponse.svar}.",
            )
        }
        return response.body!!
    }

    private fun lagreMedlemskapVurdering(
        medlemskapVurderingRequest: MedlemskapVurderingRequest,
        medlemskapVurderingResponse: MedlemskapVurderingResponse,
        svarTid: Long,
    ) {
        medlemskapVurderingRepository.save(
            MedlemskapVurderingDbRecord(
                timestamp = Instant.now(),
                svartid = svarTid,
                fnr = medlemskapVurderingRequest.fnr,
                fom = medlemskapVurderingRequest.fom,
                tom = medlemskapVurderingRequest.tom,
                svartype = medlemskapVurderingResponse.svar.toString(),
                sporsmal = medlemskapVurderingResponse.sporsmal.takeIf { it.isNotEmpty() }?.tilPostgresJson(),
                sykepengesoknadId = medlemskapVurderingRequest.sykepengesoknadId,
                kjentOppholdstillatelse = medlemskapVurderingResponse.kjentOppholdstillatelse?.tilPostgresJson(),
            ),
        )
    }
}

/**
 * Kastes når det oppstår en teknisk feil ved kall til LovMe.
 */
class MedlemskapVurderingClientException(
    message: String?,
    cause: Throwable?,
) : RuntimeException(message, cause)

/**
 * Kastes når LovMe returnerer en feilmelding / statuskode som ikke er 2xx som følge av en logisk feil.
 */
class MedlemskapVurderingResponseException(
    message: String?,
) : RuntimeException(message)

data class MedlemskapVurderingRequest(
    var fnr: String,
    var fom: LocalDate,
    var tom: LocalDate,
    var sykepengesoknadId: String,
)

data class MedlemskapVurderingResponse(
    var svar: MedlemskapVurderingSvarType,
    val sporsmal: List<MedlemskapVurderingSporsmal>,
    // Blir bare satt hvis vi får returnert spørsmål om oppholdstillatelse.
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
)

data class KjentOppholdstillatelse(
    val fom: LocalDate,
    // Oppholdstillatelsen har ikke sluttdato hvis den er permanent.
    val tom: LocalDate? = null,
)

enum class MedlemskapVurderingSvarType {
    JA,
    NEI,
    UAVKLART,
}

enum class MedlemskapVurderingSporsmal {
    OPPHOLDSTILATELSE,
    ARBEID_UTENFOR_NORGE,
    OPPHOLD_UTENFOR_EØS_OMRÅDE,
    OPPHOLD_UTENFOR_NORGE,
}
