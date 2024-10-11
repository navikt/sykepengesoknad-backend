package no.nav.helse.flex.client.sigrun

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.toJsonNode
import java.util.*

data class HentPensjonsgivendeInntektResponse(
    val norskPersonidentifikator: String,
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
) {
    @Override
    fun toJsonNode(): JsonNode {
        return objectMapper.createObjectNode().apply {
            put("inntektsaar", inntektsaar)
            set<JsonNode>("pensjonsgivendeInntekt", pensjonsgivendeInntekt.map { it.toJsonNode() }.toJsonNode())
        }
    }
}

data class PensjonsgivendeInntekt(
    val datoForFastsetting: String,
    val skatteordning: Skatteordning,
    val pensjonsgivendeInntektAvLoennsinntekt: Int?,
    val pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel: Int?,
    val pensjonsgivendeInntektAvNaeringsinntekt: Int?,
    val pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage: Int?,
) {
    fun sumAvAlleInntekter(): Int {
        return (pensjonsgivendeInntektAvLoennsinntekt ?: 0) +
            (pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel ?: 0) +
            (pensjonsgivendeInntektAvNaeringsinntekt ?: 0) +
            (pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage ?: 0)
    }

    @Override
    fun toJsonNode(): JsonNode {
        return objectMapper.createObjectNode().apply {
            put("datoForFastsetting", datoForFastsetting)
            put("skatteordning", skatteordning.name)
            put("loenn", pensjonsgivendeInntektAvLoennsinntekt)
            put("loenn-bare-pensjon", pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel)
            put("naering", pensjonsgivendeInntektAvNaeringsinntekt)
            put("fiske-fangst-familiebarnehage", pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage)
        }
    }
}

enum class Skatteordning {
    FASTLAND,
    SVALBARD,
    KILDESKATT_PAA_LOENN,
}

data class Feil(
    val kode: String,
    val melding: String,
    val korrelasjonsid: UUID,
)
