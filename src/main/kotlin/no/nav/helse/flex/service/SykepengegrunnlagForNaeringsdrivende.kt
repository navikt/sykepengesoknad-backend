package no.nav.helse.flex.service

import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClient
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service

private const val SIGRUN_DATA_TIDLIGSTE_AAR = 2017

@Service
class SykepengegrunnlagForNaeringsdrivende(
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
) {
    private val log = logger()

    fun beregnSykepengegrunnlag(soknad: Sykepengesoknad): SykepengegrunnlagNaeringsdrivende? {
        val sykmeldingsAar = soknad.startSykeforlop!!.year

        val pensjonsgivendeInntekter =
            hentRelevantPensjonsgivendeInntekt(
                soknad.fnr,
                soknad.id,
                sykmeldingsAar,
            )
        if (pensjonsgivendeInntekter == null) {
            return null
        }

        val aaretFoerSykepengegrunnlaget = pensjonsgivendeInntekter.finnFoersteAarISykepengegrunnlaget() - 1
        val harFunnetInntektFoerSykepengegrunnlaget =
            finnesPensjonsgivendeInntektForAar(
                fnr = soknad.fnr,
                aar = aaretFoerSykepengegrunnlaget,
            )

        return SykepengegrunnlagNaeringsdrivende(
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
}

data class SykepengegrunnlagNaeringsdrivende(
    val inntekter: List<HentPensjonsgivendeInntektResponse>,
    val harFunnetInntektFoerSykepengegrunnlaget: Boolean = false,
)

fun List<HentPensjonsgivendeInntektResponse>.finnFoersteAarISykepengegrunnlaget(): Int = this.minOf { it.inntektsaar.toInt() }
