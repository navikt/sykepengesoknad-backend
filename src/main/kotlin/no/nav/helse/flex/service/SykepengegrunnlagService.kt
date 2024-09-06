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
            log.info("Finner sykepengegrunnlag for selvstendig næringsdrivende ${soknad.id}")
            val grunnbeloepSisteFemAar =
                grunnbeloepService.hentHistorikkSisteFemAar().block()?.takeIf { it.isNotEmpty() }
                    ?: throw Exception("finner ikke historikk for g fra siste fem år")
            log.info("Grunnbeløp siste 5 år: ${grunnbeloepSisteFemAar.serialisertTilString()}")

            val sykmeldingstidspunkt =
                soknad.startSykeforlop?.year
                    ?: throw Exception("Fant ikke sykmeldingstidspunkt")

            val grunnbeloepPaaSykmeldingstidspunkt =
                grunnbeloepSisteFemAar.find { it.dato.tilAar() == sykmeldingstidspunkt }?.grunnbeløp?.takeIf { it > 0 }
                    ?: throw Exception("Fant ikke g på sykmeldingstidspunkt $sykmeldingstidspunkt")
            log.info("Grunnbeløp på sykemeldingstidspunkt $sykmeldingstidspunkt $grunnbeloepPaaSykmeldingstidspunkt")

            val pensjonsgivendeInntekter =
                hentPensjonsgivendeInntektForTreSisteArene(soknad.fnr, sykmeldingstidspunkt)
                    ?: throw Exception("Fant ikke 3 år med pensjonsgivende inntekter for ${soknad.fnr}")
            log.info("Pensjonsgivende inntekter siste 3 år for fnr ${soknad.fnr}: ${pensjonsgivendeInntekter.serialisertTilString()}")

            val beregnetInntektPerAar =
                finnBeregnetInntektPerAar(
                    pensjonsgivendeInntekter,
                    grunnbeloepSisteFemAar,
                    grunnbeloepPaaSykmeldingstidspunkt,
                )
            log.info("Beregnet inntekt per år: ${beregnetInntektPerAar.serialisertTilString()}")

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
        val treFerdigliknetAr = mutableListOf<HentPensjonsgivendeInntektResponse>()
        var arViHarSjekket = 0
        var ingenInntektCounter = 0
        var forsteArHarVerdi = false

        for (yearOffset in 0..3) {
            if (treFerdigliknetAr.size == 3) {
                break
            }

            val arViHenterFor = sykmeldingstidspunkt - yearOffset - 1
            val svar =
                try {
                    pensjongivendeInntektClient.hentPensjonsgivendeInntekt(fnr, arViHenterFor)
                } catch (e: IngenPensjonsgivendeInntektFunnetException) {
                    ingenInntektCounter++
                    HentPensjonsgivendeInntektResponse(
                        fnr,
                        arViHenterFor.toString(),
                        emptyList(),
                    )
                }

            if (svar.pensjonsgivendeInntekt.isNotEmpty()) {
                treFerdigliknetAr.add(svar)
                if (arViHarSjekket == 0) {
                    forsteArHarVerdi = true
                }
            } else if (arViHarSjekket == 0) {
                ingenInntektCounter++
            }

            arViHarSjekket++

            // Sjekk om vi skal avbryte
            if (forsteArHarVerdi && ingenInntektCounter == 2 && treFerdigliknetAr.size == 1) {
                break
            }
            if (treFerdigliknetAr.size == 2 && svar.pensjonsgivendeInntekt.isNotEmpty() && ingenInntektCounter == 1) {
                continue // Fortsett å hente for det fjerde året
            }
        }

        return if (treFerdigliknetAr.size == 3 ||
            (
                forsteArHarVerdi &&
                    treFerdigliknetAr.size == 2 &&
                    ingenInntektCounter == 1
            )
        ) {
            treFerdigliknetAr
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
