package no.nav.helse.flex.domain

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
                sykepengegrunnlagNaeringsdrivende = null,
            )
        }

        return SelvstendigNaringsdrivendeDTO(
            roller = roller.map { RolleDTO(it.orgnummer, it.rolletype) },
            sykepengegrunnlagNaeringsdrivende =
                SykepengegrunnlagNaeringsdrivendeDTO(
                    gjennomsnittPerAar = grunnlag.gjennomsnittPerAar.map { AarVerdiDTO(it.aar, it.verdi) },
                    grunnbeloepPerAar = grunnlag.grunnbeloepPerAar.map { AarVerdiDTO(it.aar, it.verdi) },
                    grunnbeloepPaaSykmeldingstidspunkt = grunnlag.grunnbeloepPaaSykmeldingstidspunkt,
                    beregnetSnittOgEndring25 =
                        BeregnetDTO(
                            snitt = grunnlag.beregnetSnittOgEndring25.snitt,
                            p25 = grunnlag.beregnetSnittOgEndring25.p25,
                            m25 = grunnlag.beregnetSnittOgEndring25.m25,
                        ),
                    inntekter =
                        grunnlag.inntekter.map { hentPensjonsgivendeInntektResponse ->
                            HentPensjonsgivendeInntektResponseDTO(
                                norskPersonidentifikator = hentPensjonsgivendeInntektResponse.norskPersonidentifikator,
                                inntektsaar = hentPensjonsgivendeInntektResponse.inntektsaar,
                                pensjonsgivendeInntekt =
                                    hentPensjonsgivendeInntektResponse.pensjonsgivendeInntekt.map {
                                        PensjonsgivendeInntektDTO(
                                            datoForFastsetting = it.datoForFastsetting,
                                            skatteordning = SkatteordningDTO.valueOf(it.skatteordning.name),
                                            pensjonsgivendeInntektAvLoennsinntekt = it.pensjonsgivendeInntektAvLoennsinntekt,
                                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel =
                                                it.pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel,
                                            pensjonsgivendeInntektAvNaeringsinntekt = it.pensjonsgivendeInntektAvNaeringsinntekt,
                                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage =
                                                it.pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage,
                                        )
                                    },
                            )
                        },
                ),
        )
    }
}

data class BrregRolle(
    val orgnummer: String,
    val orgnavn: String,
    val rolletype: String,
)
