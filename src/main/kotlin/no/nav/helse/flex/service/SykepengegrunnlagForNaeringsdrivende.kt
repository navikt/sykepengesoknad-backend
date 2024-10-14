package no.nav.helse.flex.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.IngenPensjonsgivendeInntektFunnetException
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClient
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.*
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.LocalDate

data class AarVerdi(
    val aar: String,
    val verdi: BigInteger,
)

data class Beregnet(
    val snitt: BigInteger,
    val p25: BigInteger,
    val m25: BigInteger,
)

data class SykepengegrunnlagNaeringsdrivende(
    val gjennomsnittPerAar: List<AarVerdi>,
    val grunnbeloepPerAar: List<AarVerdi>,
    val grunnbeloepPaaSykmeldingstidspunkt: Int,
    val beregnetSnittOgEndring25: Beregnet,
    val inntekter: List<HentPensjonsgivendeInntektResponse>,
) {
    @Override
    fun toJsonNode(): JsonNode {
        return objectMapper.createObjectNode().apply {
            set<JsonNode>(
                "sigrunInntekt",
                objectMapper.createObjectNode().apply {
                    set<JsonNode>("inntekter", gjennomsnittPerAar.map { it.toJsonNode() }.toJsonNode())
                    set<JsonNode>("g-verdier", grunnbeloepPerAar.map { it.toJsonNode() }.toJsonNode())
                    put("g-sykmelding", grunnbeloepPaaSykmeldingstidspunkt)
                    set<JsonNode>("beregnet", beregnetSnittOgEndring25.toJsonNode())
                    set<JsonNode>("original-inntekt", inntekter.map { it.toJsonNode() }.toJsonNode())
                },
            )
        }
    }
}

@Service
class SykepengegrunnlagForNaeringsdrivende(
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

            val grunnbeloepRelevanteAar = finnGrunnbeloepForTreRelevanteAar(grunnbeloepSisteFemAar, pensjonsgivendeInntekter)
            val inntekterJustertForGrunnbeloep =
                finnInntekterJustertForGrunnbeloep(
                    pensjonsgivendeInntekter,
                    grunnbeloepRelevanteAar,
                    grunnbeloepPaaSykmeldingstidspunkt,
                )

            val justerteInntekter =
                finnInntekterJustertFor6Gog12G(
                    inntekterJustertForGrunnbeloep,
                    grunnbeloepPaaSykmeldingstidspunkt,
                )
            val gjennomsnittligInntektAlleAar = beregnGjennomsnittligInntekt(justerteInntekter)

            return SykepengegrunnlagNaeringsdrivende(
                gjennomsnittPerAar = justerteInntekter.map { AarVerdi(it.key, it.value.roundToBigInteger()) },
                grunnbeloepPerAar = grunnbeloepRelevanteAar.map { AarVerdi(it.key, it.value) },
                grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepPaaSykmeldingstidspunkt,
                beregnetSnittOgEndring25 = beregnEndring25Prosent(gjennomsnittligInntektAlleAar),
                inntekter = pensjonsgivendeInntekter,
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
        inntektPerAar: List<HentPensjonsgivendeInntektResponse>,
    ): Map<String, BigInteger> {
        val relevanteInntektsAar = inntektPerAar.map { it.inntektsaar.toInt() }

        return grunnbeloepSisteFemAar
            .filter { it.dato.tilAar() in relevanteInntektsAar }
            .associate { it.dato.tilAar().toString() to it.gjennomsnittPerÅr.toBigInteger() }
    }

    private fun finnInntekterJustertForGrunnbeloep(
        pensjonsgivendeInntekter: List<HentPensjonsgivendeInntektResponse>,
        grunnbeloepRelevanteAar: Map<String, BigInteger>,
        grunnbeloepSykmldTidspunkt: Int,
    ): Map<String, BigInteger> {
        return pensjonsgivendeInntekter.associate { inntekt ->
            val grunnbeloepForAaret =
                grunnbeloepRelevanteAar[inntekt.inntektsaar]
                    ?: throw Exception("Finner ikke grunnbeløp for inntektsår ${inntekt.inntektsaar}")

            inntekt.inntektsaar to
                inntektJustertForGrunnbeloep(
                    pensjonsgivendeInntektIKalenderAaret =
                        inntekt.pensjonsgivendeInntekt.sumOf { it.sumAvAlleInntekter() }
                            .toBigInteger(),
                    gPaaSykmeldingstidspunktet = grunnbeloepSykmldTidspunkt.toBigInteger(),
                    gjennomsnittligGIKalenderaaret = grunnbeloepForAaret,
                )
        }
    }

    fun String.tilAar() = LocalDate.parse(this).year
}
