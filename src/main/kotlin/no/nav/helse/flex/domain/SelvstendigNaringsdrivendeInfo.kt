package no.nav.helse.flex.domain

import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.*
import java.time.LocalDate

data class SelvstendigNaringsdrivendeInfo(
    val roller: List<BrregRolle>,
    val sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende? = null,
    val ventetid: Ventetid? = null,
) {
    fun tilSelvstendigNaringsdrivendeDTO(): SelvstendigNaringsdrivendeDTO {
        val inntekt =
            sykepengegrunnlagNaeringsdrivende?.let {
                InntektDTO(
                    norskPersonidentifikator = it.inntekter.first().norskPersonidentifikator,
                    inntektsAar = tilNaringsdrivendeInntektsAarDTO(),
                )
            }

        return SelvstendigNaringsdrivendeDTO(
            roller = roller.map { RolleDTO(it.orgnummer, it.rolletype) },
            inntekt = inntekt,
            ventetid = ventetid?.let { tilVentetidDTO() },
        )
    }

    private fun tilVentetidDTO(): VentetidDTO =
        VentetidDTO(
            fom = ventetid!!.fom,
            tom = ventetid.tom,
        )

    private fun tilNaringsdrivendeInntektsAarDTO(): List<InntektsAarDTO> =
        sykepengegrunnlagNaeringsdrivende!!.inntekter.map { inntekt ->
            InntektsAarDTO(
                aar = inntekt.inntektsaar,
                // Summerer pensjonsgivende inntekt fra FASTLAND og SVALBARD.
                pensjonsgivendeInntekt =
                    if (inntekt.pensjonsgivendeInntekt.isNotEmpty()) summerPensjonsgivendeInntekt(inntekt.pensjonsgivendeInntekt) else null,
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

data class Ventetid(
    val fom: LocalDate,
    val tom: LocalDate,
)
