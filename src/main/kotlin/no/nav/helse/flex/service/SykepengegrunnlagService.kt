package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.inntektskomponenten.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.inntektskomponenten.PensjongivendeInntektClient
import no.nav.helse.flex.client.inntektskomponenten.PensjonsgivendeInntekt
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.sykepengegrunnlagUtregner
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.LocalDate

data class SykepengegrunnlagNaeringsdrivende(
    val gjennomsnittTotal: BigInteger,
    val gjennomsnittPerAar: Map<String, BigInteger>,
    val grunnbeloepPerAar: Map<String, BigInteger>,
)

@Service
class SykepengegrunnlagService(
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
    private val grunnbeloepService: GrunnbeloepService,
) {
    private val log = logger()

    fun sykepengegrunnlagNaeringsdrivende(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende {
        val grunnbeloepSisteFemAar =
            grunnbeloepService.hentHistorikkSisteFemAar().block()
                ?: throw Exception("finner ikke historikk for g fra siste fem år")

        val grunnbeloepSykmldTidspunkt =
            grunnbeloepSisteFemAar.find { LocalDate.parse(it.dato).year == soknad.aktivertDato?.year }?.grunnbeloep
                ?: throw Exception("Fant ikke g på sykmeldingstidspunkt")

        val pensjonsgivendeInntekter =
            pensjongivendeInntektClient.hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr)
                ?: throw Exception("Fant ikke pensjonsgivendeInntekter for ${soknad.fnr}")

        val beregnetInntektPerAar =
            pensjonsgivendeInntekter.mapNotNull { inntekt ->
                val aar = inntekt.inntektsaar
                val grunnbeloepForAaret =
                    grunnbeloepSisteFemAar.find { LocalDate.parse(it.dato).year.toString() == aar }
                        ?: return@mapNotNull null

                aar to finnInntektForAaret(inntekt, grunnbeloepSykmldTidspunkt, grunnbeloepForAaret)
            }.toMap()

        val gjennomsnittligInntektAlleAar = beregnetInntektPerAar.values.sumOf { it.toInt() } / 3

        val grunnbeloepForRelevanteTreAar =
            grunnbeloepSisteFemAar.filter { LocalDate.parse(it.dato).year.toString() in beregnetInntektPerAar.keys }.associate {
                    grunnbeloepResponse ->
                grunnbeloepResponse.dato to grunnbeloepResponse.gjennomsnittPerAar.toBigInteger()
            }

        return SykepengegrunnlagNaeringsdrivende(
            gjennomsnittTotal = gjennomsnittligInntektAlleAar.toBigInteger(),
            gjennomsnittPerAar = beregnetInntektPerAar,
            grunnbeloepPerAar = grunnbeloepForRelevanteTreAar,
        )
    }

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
