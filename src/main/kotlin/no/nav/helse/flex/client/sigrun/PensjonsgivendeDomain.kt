package no.nav.helse.flex.client.sigrun

import java.util.UUID

data class HentPensjonsgivendeInntektResponse(
    val norskPersonidentifikator: String,
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
)

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
