package no.nav.helse.flex.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.sigrun.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.*
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.LocalDate

data class SykepengegrunnlagNaeringsdrivende(
    val gjennomsnittTotal: BigInteger,
    val gjennomsnittPerAar: Map<String, BigInteger>,
    val grunnbeloepPerAar: Map<String, BigInteger>,
    val grunnbeloepPaaSykmeldingstidspunkt: Int,
    val endring25Prosent: List<BigInteger>,
) {
    @Override
    fun toJsonNode(): JsonNode {
        val rootObject: ObjectNode = JsonNodeFactory.instance.objectNode()
        val objectNode: ObjectNode = JsonNodeFactory.instance.objectNode()

        val inntekterArray: ArrayNode = objectNode.putArray("inntekter")
        gjennomsnittPerAar.forEach { (year, amount) ->
            val inntektNode: ObjectNode = JsonNodeFactory.instance.objectNode()
            inntektNode.put("aar", year)
            inntektNode.put("verdi", amount)
            inntekterArray.add(inntektNode)
        }

        val gVerdierArray: ArrayNode = objectNode.putArray("g-verdier")
        grunnbeloepPerAar.forEach { (year, amount) ->
            val gVerdiNode: ObjectNode = JsonNodeFactory.instance.objectNode()
            gVerdiNode.put("aar", year)
            gVerdiNode.put("verdi", amount)
            gVerdierArray.add(gVerdiNode)
        }

        objectNode.put("g-sykmelding", grunnbeloepPaaSykmeldingstidspunkt)

        val beregnetNode: ObjectNode = JsonNodeFactory.instance.objectNode()
        beregnetNode.put("snitt", gjennomsnittTotal)
        beregnetNode.put("p25", endring25Prosent.getOrNull(0))
        beregnetNode.put("m25", endring25Prosent.getOrNull(1))

        objectNode.set<ObjectNode>("beregnet", beregnetNode)

        rootObject.set<ObjectNode>("sigrunInntekt", objectNode)
        return rootObject
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
                gjennomsnittTotal = gjennomsnittligInntektAlleAar.roundToBigInteger(),
                gjennomsnittPerAar = justerteInntekter.map { it.key to it.value.roundToBigInteger() }.toMap(),
                grunnbeloepPerAar = grunnbeloepRelevanteAar,
                grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepPaaSykmeldingstidspunkt,
                endring25Prosent = beregnEndring25Prosent(gjennomsnittligInntektAlleAar),
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
