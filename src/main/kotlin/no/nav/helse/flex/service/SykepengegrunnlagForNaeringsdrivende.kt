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
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

// TODO: Flytt dataklasser nederst og gi klassene en mer beskrivende navn.
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
    // TODO: Rename metodenavn sånn at det beskriver hva som skjer.
    fun sykepengegrunnlagNaeringsdrivende(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende? {
        // TODO: Rename eller bruk faktisk startSyketilfelle.
        val sykmeldingstidspunkt = soknad.startSykeforlop!!.year

        val grunnbeloepSisteFemAar = grunnbeloepService.hentGrunnbeloepHistorikk(sykmeldingstidspunkt)

        val grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepSisteFemAar[sykmeldingstidspunkt]!!.grunnbeløp

        val pensjonsgivendeInntekter =
            hentPensjonsgivendeInntektForTreSisteArene(
                soknad.fnr,
                sykmeldingstidspunkt,
                soknad.id,
            )?.filter { it.pensjonsgivendeInntekt.isNotEmpty() }

        if (pensjonsgivendeInntekter.isNullOrEmpty()) {
            return null
        }

        val grunnbeloepRelevanteAar =
            finnGrunnbeloepForTreRelevanteAar(grunnbeloepSisteFemAar, pensjonsgivendeInntekter)

        val inntekterJustertForGrunnbeloep =
            finnInntekterJustertForGrunnbeloep(
                pensjonsgivendeInntekter,
                grunnbeloepRelevanteAar,
                grunnbeloepPaaSykmeldingstidspunkt,
            )

        val justerteInntekter =
            finnInntekterJustertFor6Gog12G(
                grunnbeloepPaaSykmeldingstidspunkt,
                inntekterJustertForGrunnbeloep,
            )
        val gjennomsnittligInntektAlleAar = beregnGjennomsnittligInntekt(justerteInntekter)

        return SykepengegrunnlagNaeringsdrivende(
            gjennomsnittPerAar = justerteInntekter.map { AarVerdi(it.key, it.value.roundToBigInteger()) },
            grunnbeloepPerAar = grunnbeloepRelevanteAar.map { AarVerdi(it.key, it.value) },
            grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepPaaSykmeldingstidspunkt,
            beregnetSnittOgEndring25 = beregnEndring25Prosent(gjennomsnittligInntektAlleAar),
            inntekter = pensjonsgivendeInntekter,
        )
    }

    private val log = logger()

    fun hentPensjonsgivendeInntektForTreSisteArene(
        fnr: String,
        sykmeldingstidspunkt: Int,
        sykepengesoknadId: String? = null,
    ): List<HentPensjonsgivendeInntektResponse>? {
        val ferdigliknetInntekter = mutableListOf<HentPensjonsgivendeInntektResponse>()
        val forsteAar = sykmeldingstidspunkt - 1
        val aarViHenterFor = forsteAar downTo forsteAar - 2

        val debugSoknader = listOf("ea417973-9a79-3972-afd0-13496955d7cf", "b1577e6d-4993-3f9d-afe1-07cc150c90d2")

        if (debugSoknader.contains(sykepengesoknadId)) {
            log.info("Henter for aar: $aarViHenterFor for sykepengesoknadId: $sykepengesoknadId")
        }

        aarViHenterFor.forEach { aar ->
            if (debugSoknader.contains(sykepengesoknadId)) {
                log.info("Henter inntekt for aar: $aar for sykepengesoknadId: $sykepengesoknadId")
            }
            val pensjonsgivendeInntektResponse =
                try {
                    pensjongivendeInntektClient.hentPensjonsgivendeInntekt(fnr, aar, sykepengesoknadId)
                    // TODO: Flytt opprettelse av tom HentPensjonsgivendeInntektResponse til klient i stedet for å bruke Exceptions til logisk håndtering.
                    // TODO: Metoden vi kaller kaster en RuntimeException hvis ingen inntekt er funnet på grunn av ulovlig år (2017). Det bobler opp.
                    // TODO: Hvorfor catcher vi ikke IngenPensjonsgivendeInntektFunnetException her for b1577e6d-4993-3f9d-afe1-07cc150c90d2?
                } catch (_: IngenPensjonsgivendeInntektFunnetException) {
                    // At ingen inntekt er funnet for det aktuelle året det betyr ikke at noe faktisk er feil.
                    HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = aar.toString(),
                        pensjonsgivendeInntekt = emptyList(),
                    )
                } catch (e: Exception) {
                    if (debugSoknader.contains(sykepengesoknadId)) {
                        log.info(
                            "Catchet ikke IngenPensjonsgivendeInntektFunnetException for " +
                                "sykepengesoknadId: $sykepengesoknadId, men en exception av type ${e::class.simpleName}",
                        )
                    }
                    throw e
                }

            ferdigliknetInntekter.add(pensjonsgivendeInntektResponse)
        }
        // Hvis det første året vi hentet er tomt henter vi for ett år ekstra for å ha tre sammenhengende år med inntekt.
        if (ferdigliknetInntekter.find { it.inntektsaar == forsteAar.toString() }?.pensjonsgivendeInntekt!!.isEmpty()) {
            return (
                ferdigliknetInntekter.slice(1..2) +
                    listOf(
                        pensjongivendeInntektClient.hentPensjonsgivendeInntekt(
                            fnr,
                            forsteAar - 3,
                        ),
                    )
            ).innholdEllerNullHvisTom()
        }
        return ferdigliknetInntekter.innholdEllerNullHvisTom()
    }

    private fun List<HentPensjonsgivendeInntektResponse>.innholdEllerNullHvisTom(): List<HentPensjonsgivendeInntektResponse>? {
        return if (this.any { it.pensjonsgivendeInntekt.isEmpty() }) {
            null
        } else {
            this
        }
    }

    private fun finnGrunnbeloepForTreRelevanteAar(
        grunnbeloepSisteFemAar: Map<Int, GrunnbeloepResponse>,
        inntektPerAar: List<HentPensjonsgivendeInntektResponse>,
    ): Map<String, BigInteger> {
        val relevanteInntektsAar = inntektPerAar.map { it.inntektsaar.toInt() }

        return grunnbeloepSisteFemAar
            .filterKeys { it in relevanteInntektsAar }
            .mapKeys { it.key.toString() }
            .mapValues { it.value.gjennomsnittPerÅr.toBigInteger() }
    }

    private fun finnInntekterJustertForGrunnbeloep(
        pensjonsgivendeInntekter: List<HentPensjonsgivendeInntektResponse>,
        grunnbeloepRelevanteAar: Map<String, BigInteger>,
        grunnbeloepSykmldTidspunkt: Int,
    ): Map<String, BigDecimal> {
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
