package no.nav.helse.flex.client.sigrun

import java.util.*

data class HentPensjonsgivendeInntektResponse(
    val norskPersonidentifikator: String,
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
)

data class PensjonsgivendeInntekt(
    val datoForFastsetting: String,
    val skatteordning: Skatteordning,
    val pensjonsgivendeInntektAvLoennsinntekt: Int = 0,
    val pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel: Int = 0,
    val pensjonsgivendeInntektAvNaeringsinntekt: Int = 0,
    val pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage: Int = 0,
) {
    fun sumAvAlleInntekter(): Int =
        pensjonsgivendeInntektAvLoennsinntekt +
            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel +
            pensjonsgivendeInntektAvNaeringsinntekt +
            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage
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
