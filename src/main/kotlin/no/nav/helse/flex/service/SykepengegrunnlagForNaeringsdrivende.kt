package no.nav.helse.flex.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClient
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.beregnEndring25Prosent
import no.nav.helse.flex.util.beregnGjennomsnittligInntekt
import no.nav.helse.flex.util.finnInntekterJustertFor6Gog12G
import no.nav.helse.flex.util.inntektJustertForGrunnbeloep
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.roundToBigInteger
import no.nav.helse.flex.util.toJsonNode
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger

private const val SIGRUN_DATA_TIDLIGSTE_AAR = 2017

@Service
class SykepengegrunnlagForNaeringsdrivende(
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
    private val grunnbeloepService: GrunnbeloepService,
) {
    private val log = logger()

    fun beregnSykepengegrunnlag(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende? {
        val sykmeldingsAar = soknad.startSykeforlop!!.year
        val grunnbeloepHistorikk = grunnbeloepService.hentGrunnbeloepHistorikk(sykmeldingsAar)

        val hentForAar =
            grunnbeloepHistorikk[sykmeldingsAar]?.let { sykmeldingsAar } ?: (sykmeldingsAar - 1).also {
                log.info("Bruker grunnbeløp for $it i stedet for $sykmeldingsAar for søknad: ${soknad.id}")
            }
        val grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepHistorikk[hentForAar]!!.grunnbeløp

        val pensjonsgivendeInntekter =
            hentRelevantPensjonsgivendeInntekt(
                soknad.fnr,
                soknad.id,
                sykmeldingsAar,
            )
        if (pensjonsgivendeInntekter == null) {
            return null
        }

        val harFunnetInntektFoerSykepengegrunnlaget =
            finnesPensjonsgivendeInntektForAar(
                fnr = soknad.fnr,
                aar = pensjonsgivendeInntekter.map { it.inntektsaar.toInt() }.minOf { it } - 1,
            )

        val grunnbeloepForAarMedInntekt =
            finnGrunnbeloepForAarMedInntekt(grunnbeloepHistorikk, pensjonsgivendeInntekter)

        val inntekterJustertForGrunnbeloep =
            finnInntekterJustertForGrunnbeloep(
                pensjonsgivendeInntekter,
                grunnbeloepForAarMedInntekt,
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
            grunnbeloepPerAar = grunnbeloepForAarMedInntekt.map { AarVerdi(it.key, it.value) },
            grunnbeloepPaaSykmeldingstidspunkt = grunnbeloepPaaSykmeldingstidspunkt,
            beregnetSnittOgEndring25 = beregnEndring25Prosent(gjennomsnittligInntektAlleAar),
            inntekter = pensjonsgivendeInntekter,
            harFunnetInntektFoerSykepengegrunnlaget = harFunnetInntektFoerSykepengegrunnlaget,
        )
    }

    fun hentRelevantPensjonsgivendeInntekt(
        fnr: String,
        sykepengesoknadId: String,
        sykmeldtAar: Int,
    ): List<HentPensjonsgivendeInntektResponse>? {
        val ferdigliknetInntekter = mutableListOf<HentPensjonsgivendeInntektResponse>()
        val forsteAar = sykmeldtAar - 1
        val aarViHenterFor = forsteAar downTo forsteAar - 2

        // Sikrer at vi ikke henter inntekt tidligere enn Sigrun har data for.
        if (aarViHenterFor.last < SIGRUN_DATA_TIDLIGSTE_AAR) {
            log.info(
                "Henter ikke pensjonsgivende inntekt for søknad $sykepengesoknadId da tidligste år er ${aarViHenterFor.last}.",
            )
            return null
        }

        aarViHenterFor.forEach { aar ->
            ferdigliknetInntekter.add(pensjongivendeInntektClient.hentPensjonsgivendeInntekt(fnr, aar))
        }

        // Hvis det første året vi hentet ikke har inntekt hentes ett år ekstra for å ha tre sammenhengende år med inntekt.
        if (ferdigliknetInntekter.find { it.inntektsaar == forsteAar.toString() }?.pensjonsgivendeInntekt!!.isEmpty()) {
            val nyttForsteAar = forsteAar - 3

            // Sikrer at vi ikke henter inntekt tidligere enn Sigrun har data for, også når vi hopper over første året.
            if (nyttForsteAar < SIGRUN_DATA_TIDLIGSTE_AAR) {
                log.info(
                    "Henter ikke pensjonsgivende inntekt for søknad $sykepengesoknadId da tidligste år er ${aarViHenterFor.last}.",
                )
                return null
            }

            return (
                ferdigliknetInntekter.slice(1..2) +
                    listOf(
                        pensjongivendeInntektClient.hentPensjonsgivendeInntekt(
                            fnr,
                            nyttForsteAar,
                        ),
                    )
            )
        }
        return ferdigliknetInntekter
    }

    fun finnesPensjonsgivendeInntektForAar(
        fnr: String,
        aar: Int,
    ): Boolean {
        if (aar < SIGRUN_DATA_TIDLIGSTE_AAR) {
            return false
        }

        return pensjongivendeInntektClient
            .hentPensjonsgivendeInntekt(fnr, aar)
            .pensjonsgivendeInntekt
            .sumOf { it.sumAvAlleInntekter() } > 0
    }

    private fun finnGrunnbeloepForAarMedInntekt(
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
    ): Map<String, BigDecimal> =
        pensjonsgivendeInntekter.associate { inntekt ->
            val grunnbeloepForAaret =
                grunnbeloepRelevanteAar[inntekt.inntektsaar]
                    ?: throw Exception("Finner ikke grunnbeløp for inntektsår ${inntekt.inntektsaar}")

            inntekt.inntektsaar to
                inntektJustertForGrunnbeloep(
                    pensjonsgivendeInntektIKalenderAaret =
                        inntekt.pensjonsgivendeInntekt
                            .sumOf { it.sumAvAlleInntekter() }
                            .toBigInteger(),
                    gPaaSykmeldingstidspunktet = grunnbeloepSykmldTidspunkt.toBigInteger(),
                    gjennomsnittligGIKalenderaaret = grunnbeloepForAaret,
                )
        }
}

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
    val harFunnetInntektFoerSykepengegrunnlaget: Boolean = false,
) {
    @Override
    fun toJsonNode(): JsonNode =
        objectMapper.createObjectNode().apply {
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
