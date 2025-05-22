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
                naringsdrivendeInntekt = null,
            )
        }

        return SelvstendigNaringsdrivendeDTO(
            roller = roller.map { RolleDTO(it.orgnummer, it.rolletype) },
            naringsdrivendeInntekt =
                NaringsdrivendeInntektDTO(
                    norskPersonidentifikator = sykepengegrunnlagNaeringsdrivende.inntekter.first().norskPersonidentifikator,
                    inntekt = mapToNaringsdrivendeInntektsAarDTO(),
                ),
        )
    }

    private fun mapToNaringsdrivendeInntektsAarDTO(): List<NaringsdrivendeInntektsAarDTO> =
        sykepengegrunnlagNaeringsdrivende!!.inntekter.map { inntekt ->
            NaringsdrivendeInntektsAarDTO(
                inntektsaar = inntekt.inntektsaar,
                // Summerer pensjonsgivende inntekt fra FASTLAND og SVALBARD.
                pensjonsgivendeInntekt = summerPensjonsgivendeInntekt(inntekt.pensjonsgivendeInntekt),
            )
        }

    private fun summerPensjonsgivendeInntekt(inntekter: List<PensjonsgivendeInntekt>): SummertPensjonsgivendeInntektDTO =
        inntekter.fold(SummertPensjonsgivendeInntektDTO()) { summert, inntekt ->
            SummertPensjonsgivendeInntektDTO(
                pensjonsgivendeInntektAvLoennsinntekt = sumLoensinntekt(summert, inntekt),
                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = sumPensjonsdel(summert, inntekt),
                pensjonsgivendeInntektAvNaeringsinntekt = sumNaringsinntekt(summert, inntekt),
                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = sumFangstinntekt(summert, inntekt),
            )
        }

    private fun sumFangstinntekt(
        summert: SummertPensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage ?: 0) +
            inntekt.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage

    private fun sumNaringsinntekt(
        summert: SummertPensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvNaeringsinntekt ?: 0) +
            inntekt.pensjonsgivendeInntektAvNaeringsinntekt

    private fun sumPensjonsdel(
        summert: SummertPensjonsgivendeInntektDTO,
        inntekt: PensjonsgivendeInntekt,
    ): Int =
        (summert.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel ?: 0) +
            inntekt.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel

    private fun sumLoensinntekt(
        summert: SummertPensjonsgivendeInntektDTO,
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
