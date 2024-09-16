package no.nav.helse.flex.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.inntektskomponenten.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.inntektskomponenten.IngenPensjonsgivendeInntektFunnetException
import no.nav.helse.flex.client.inntektskomponenten.PensjongivendeInntektClient
import no.nav.helse.flex.client.inntektskomponenten.PensjonsgivendeInntekt
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.*
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.LocalDate

data class SykepengegrunnlagNaeringsdrivende(
    val fastsattSykepengegrunnlag: BigInteger,
    val gjennomsnittTotal: BigInteger,
    val gjennomsnittPerAar: Map<String, BigInteger>,
    val grunnbeloepPerAar: Map<String, BigInteger>,
    val grunnbeloepPaaSykmeldingstidspunkt: Int,
    val endring25Prosent: List<BigInteger>,
) {
    @Override
    fun toJsonNode(): JsonNode {
        val inntektNode: ObjectNode = objectMapper.createObjectNode()

        gjennomsnittPerAar.forEach { (year, amount) ->
            inntektNode.put("inntekt-$year", amount)
        }
        grunnbeloepPerAar.forEach { (year, amount) ->
            inntektNode.put("g-$year", amount)
        }

        inntektNode.put("g-sykmelding", grunnbeloepPaaSykmeldingstidspunkt)
        inntektNode.put("beregnet-snitt", gjennomsnittTotal)
        inntektNode.put("fastsatt-sykepengegrunnlag", fastsattSykepengegrunnlag)

        // p25 = pluss 25%, m25 = minus 25%
        inntektNode.put("beregnet-p25", endring25Prosent.getOrNull(0))
        inntektNode.put("beregnet-m25", endring25Prosent.getOrNull(1))

        return inntektNode
    }
}

@Service
class SykepengegrunnlagService(
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
    private val grunnbeloepService: GrunnbeloepService,
) {
    private val log = logger()

    fun sykepengegrunnlagNaeringsdrivende(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende? {
        try {
            val grunnbeloepSisteFemAar =
                grunnbeloepService.hentHistorikkSisteFemAar().block()?.takeIf { it.isNotEmpty() }
                    ?: throw Exception("finner ikke historikk for g fra siste fem år")

            val sykmeldingstidspunkt =
                soknad.startSykeforlop?.year
                    ?: throw Exception("Fant ikke sykmeldingstidspunkt for soknad ${soknad.id}")

            val grunnbeloepPaaSykmeldingstidspunkt =
                grunnbeloepSisteFemAar.find { it.dato.tilAar() == sykmeldingstidspunkt }?.grunnbeløp?.takeIf { it > 0 }
                    ?: throw Exception("Fant ikke g på sykmeldingstidspunkt for soknad ${soknad.id}")

            val pensjonsgivendeInntekter =
                hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, sykmeldingstidspunkt)
                    ?: throw Exception("Fant ikke 3 år med pensjonsgivende inntekter for person med soknad ${soknad.id}")

            val beregnetInntektPerAar =
                finnBeregnetInntektPerAar(
                    pensjonsgivendeInntekter,
                    grunnbeloepSisteFemAar,
                    grunnbeloepPaaSykmeldingstidspunkt,
                )

            val (gjennomsnittligInntektAlleAar, fastsattSykepengegrunnlag) =
                beregnGjennomsnittligInntekt(beregnetInntektPerAar, grunnbeloepPaaSykmeldingstidspunkt)

            val grunnbeloepForRelevanteTreAar =
                finnGrunnbeloepForTreRelevanteAar(grunnbeloepSisteFemAar, beregnetInntektPerAar)

            return SykepengegrunnlagNaeringsdrivende(
                fastsattSykepengegrunnlag = fastsattSykepengegrunnlag.roundToBigInteger(),
                gjennomsnittTotal = gjennomsnittligInntektAlleAar.roundToBigInteger(),
                gjennomsnittPerAar = beregnetInntektPerAar,
                grunnbeloepPerAar = grunnbeloepForRelevanteTreAar,
                grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepPaaSykmeldingstidspunkt,
                endring25Prosent = beregnEndring25Prosent(fastsattSykepengegrunnlag),
            )
        } catch (e: Exception) {
            log.error(e.message, e)
            return null
        }
    }

    fun hentPensjonsgivendeInntektForTreSisteArene(
        fnr: String,
        sykmeldingstidspunkt: Int,
    ): List<HentPensjonsgivendeInntektResponse>? {
        val ferdigliknetInntekter = mutableListOf<HentPensjonsgivendeInntektResponse>()

        for (yearOffset in 0 until 3) {
            val arViHenterFor =
                if (sykmeldingstidspunkt == LocalDate.now().year) {
                    sykmeldingstidspunkt - yearOffset - 1
                } else {
                    sykmeldingstidspunkt - yearOffset
                }

            val svar =
                try {
                    pensjongivendeInntektClient.hentPensjonsgivendeInntekt(fnr, arViHenterFor)
                } catch (e: IngenPensjonsgivendeInntektFunnetException) {
                    return null
                }

            if (svar.pensjonsgivendeInntekt.isNotEmpty()) {
                ferdigliknetInntekter.add(svar)
            } else {
                return null
            }
        }

        return if (ferdigliknetInntekter.size == 3) {
            ferdigliknetInntekter
        } else {
            null
        }
    }

    private fun finnGrunnbeloepForTreRelevanteAar(
        grunnbeloepSisteFemAar: List<GrunnbeloepResponse>,
        beregnetInntektPerAar: Map<String, BigInteger>,
    ) = grunnbeloepSisteFemAar.filter { grunnbeloepResponse ->
        grunnbeloepResponse.dato.tilAar() in beregnetInntektPerAar.keys.map { it.toInt() }
    }.associate { grunnbeloepResponse ->
        grunnbeloepResponse.dato.tilAar().toString() to grunnbeloepResponse.gjennomsnittPerÅr.toBigInteger()
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
            gjennomsnittligGIKalenderaaret = grunnbeloepForAaret.gjennomsnittPerÅr.toBigInteger(),
        )
    }

    fun String.tilAar() = LocalDate.parse(this).year
}
