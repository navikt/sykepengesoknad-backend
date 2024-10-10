package no.nav.helse.flex.client.sigrun

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class HentPensjonsgivendeInntektResponse(
    val norskPersonidentifikator: String,
    @JsonProperty("aar")
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
)

data class PensjonsgivendeInntekt(
    val datoForFastsetting: String,
    val skatteordning: Skatteordning,
    @JsonProperty("loenn")
    val pensjonsgivendeInntektAvLoennsinntekt: Int?,
    @JsonProperty("loenn-bare-pensjon")
    val pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel: Int?,
    @JsonProperty("naering")
    val pensjonsgivendeInntektAvNaeringsinntekt: Int?,
    @JsonProperty("fiske-fangst-familiebarnehage")
    val pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage: Int?,
) {
    fun sumAvAlleInntekter(): Int {
        return (pensjonsgivendeInntektAvLoennsinntekt ?: 0) +
            (pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel ?: 0) +
            (pensjonsgivendeInntektAvNaeringsinntekt ?: 0) +
            (pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage ?: 0)
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
