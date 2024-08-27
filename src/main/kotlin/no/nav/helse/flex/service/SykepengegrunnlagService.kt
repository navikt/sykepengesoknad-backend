package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.inntektskomponenten.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.inntektskomponenten.PensjongivendeInntektClient
import no.nav.helse.flex.client.inntektskomponenten.PensjonsgivendeInntekt
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.beregnGjennomsnittligInntekt
import no.nav.helse.flex.util.sykepengegrunnlagUtregner
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.LocalDate

data class SykepengegrunnlagNaeringsdrivende(
    val fastsattSykepengegrunnlag: BigInteger,
    val gjennomsnittTotal: BigInteger,
    val gjennomsnittPerAar: Map<String, BigInteger>,
    val grunnbeloepPerAar: Map<String, BigInteger>,
    val grunnbeloepPaaSykmeldingstidspunkt: Int,
)

@Service
class SykepengegrunnlagService(
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
    private val grunnbeloepService: GrunnbeloepService,
) {
    private val log = logger()

    fun sykepengegrunnlagNaeringsdrivende(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende? {
        val grunnbeloepSisteFemAar =
            grunnbeloepService.hentHistorikkSisteFemAar().block()
                ?: throw Exception("finner ikke historikk for g fra siste fem år")

        val grunnbeloepSykmldTidspunkt =
            grunnbeloepSisteFemAar.find { it.dato.tilAar() == soknad.aktivertDato?.year }?.grunnbeloep
                ?: throw Exception("Fant ikke g på sykmeldingstidspunkt")

        val pensjonsgivendeInntekter =
            pensjongivendeInntektClient.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr)
                ?: throw Exception("Fant ikke pensjonsgivendeInntekter for ${soknad.fnr}")

        val beregnetInntektPerAar =
            finnBeregnetInntektPerAar(pensjonsgivendeInntekter, grunnbeloepSisteFemAar, grunnbeloepSykmldTidspunkt)

        if (alleAarUnder1g(beregnetInntektPerAar, grunnbeloepSykmldTidspunkt)) return null

        val (gjennomsnittligInntektAlleAar, fastsattSykepengegrunnlag) =
            beregnGjennomsnittligInntekt(beregnetInntektPerAar, grunnbeloepSykmldTidspunkt)

        val grunnbeloepForRelevanteTreAar =
            grunnbeloepSisteFemAar.filter { grunnbeloepResponse ->
                grunnbeloepResponse.dato.tilAar() in beregnetInntektPerAar.keys.map { it.toInt() }
            }.associate { grunnbeloepResponse ->
                grunnbeloepResponse.dato to grunnbeloepResponse.gjennomsnittPerAar.toBigInteger()
            }

        return SykepengegrunnlagNaeringsdrivende(
            fastsattSykepengegrunnlag = fastsattSykepengegrunnlag,
            gjennomsnittTotal = gjennomsnittligInntektAlleAar,
            gjennomsnittPerAar = beregnetInntektPerAar,
            grunnbeloepPerAar = grunnbeloepForRelevanteTreAar,
            grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepSykmldTidspunkt,
        )
    }

    private fun finnBeregnetInntektPerAar(
        pensjonsgivendeInntekter: List<HentPensjonsgivendeInntektResponse>,
        grunnbeloepSisteFemAar: List<GrunnbeloepResponse>,
        grunnbeloepSykmldTidspunkt: Int,
    ): Map<String, BigInteger> {
        return pensjonsgivendeInntekter.mapNotNull { inntekt ->
            val aar = inntekt.inntektsaar
            val grunnbeloepForAaret =
                grunnbeloepSisteFemAar.find { it.dato.tilAar() == aar.toInt() }
                    ?: return@mapNotNull null

            aar to finnInntektForAaret(inntekt, grunnbeloepSykmldTidspunkt, grunnbeloepForAaret)
        }.toMap()
    }

    private fun alleAarUnder1g(
        beregnetInntektPerAar: Map<String, BigInteger>,
        grunnbeloepSykmldTidspunkt: Int,
    ): Boolean {
        return beregnetInntektPerAar.all { it.value < grunnbeloepSykmldTidspunkt.toBigInteger() }
    }

    fun String.tilAar() = LocalDate.parse(this).year

    private fun finnInntektForAaret(
        inntekt: HentPensjonsgivendeInntektResponse,
        grunnbeloepSykmldTidspunkt: Int,
        grunnbeloepForAaret: GrunnbeloepResponse,
    ): BigInteger {
        return sykepengegrunnlagUtregner(
            pensjonsgivendeInntektIKalenderAaret =
                inntekt.pensjonsgivendeInntekt.firstNotNullOf(
                    PensjonsgivendeInntekt::pensjonsgivendeInntektAvNaeringsinntekt,
                ).toBigInteger(),
            gPaaSykmeldingstidspunktet = grunnbeloepSykmldTidspunkt.toBigInteger(),
            gjennomsnittligGIKalenderaaret = grunnbeloepForAaret.gjennomsnittPerAar.toBigInteger(),
        )
    }
}
