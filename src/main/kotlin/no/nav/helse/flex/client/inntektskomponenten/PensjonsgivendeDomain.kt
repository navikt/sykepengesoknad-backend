package no.nav.helse.flex.client.inntektskomponenten

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
)

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
