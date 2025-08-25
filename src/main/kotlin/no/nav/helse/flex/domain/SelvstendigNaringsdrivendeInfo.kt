package no.nav.helse.flex.domain

import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.*

data class SelvstendigNaringsdrivendeInfo(
    val roller: List<BrregRolle>,
    val sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende? = null,
) {
    fun tilDto(): SelvstendigNaringsdrivendeDTO {
        val grunnlag = sykepengegrunnlagNaeringsdrivende
        if (grunnlag == null) {
            return SelvstendigNaringsdrivendeDTO(
                roller = roller.map { RolleDTO(it.orgnummer, it.rolletype) },
                inntekt = null,
            )
        }

        return SelvstendigNaringsdrivendeDTO(
            roller = roller.map { RolleDTO(it.orgnummer, it.rolletype) },
            inntekt =
                InntektDTO(
                    norskPersonidentifikator = sykepengegrunnlagNaeringsdrivende.inntekter.first().norskPersonidentifikator,
                    inntektsAar = mapToNaringsdrivendeInntektsAarDTO(),
                ),
        )
    }

    private fun mapToNaringsdrivendeInntektsAarDTO(): List<InntektsAarDTO> =
        sykepengegrunnlagNaeringsdrivende!!.inntekter.map { inntekt ->
            InntektsAarDTO(
                aar = inntekt.inntektsaar,
                // Summerer pensjonsgivende inntekt fra FASTLAND og SVALBARD.
                pensjonsgivendeInntekt = summerPensjonsgivendeInntekt(inntekt.pensjonsgivendeInntekt),
            )
        }

    private fun summerPensjonsgivendeInntekt(inntekter: List<PensjonsgivendeInntekt>): PensjonsgivendeInntektDTO =
        inntekter.fold(PensjonsgivendeInntektDTO()) { summert, inntekt ->
            PensjonsgivendeInntektDTO(
                pensjonsgivendeInntektAvLoennsinntekt = sumLoensinntekt(summert, inntekt),
                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = sumPensjonsdel(summert, inntekt),
                pensjonsgivendeInntektAvNaeringsinntekt = sumNaringsinntekt(summert, inntekt),
                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage =
                    sumFangstinntekt(
                        summert,
                        inntekt,
                    ),
            )
        }

    private fun sumFangstinntekt(
        summert: PensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage ?: 0) +
            inntekt.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage

    private fun sumNaringsinntekt(
        summert: PensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvNaeringsinntekt ?: 0) +
            inntekt.pensjonsgivendeInntektAvNaeringsinntekt

    private fun sumPensjonsdel(
        summert: PensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel ?: 0) +
            inntekt.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel

    private fun sumLoensinntekt(
        summert: PensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvLoennsinntekt ?: 0) +
            inntekt.pensjonsgivendeInntektAvLoennsinntekt
}

data class BrregRolle(
    val orgnummer: String,
    val orgnavn: String,
    val rolletype: String,
)
