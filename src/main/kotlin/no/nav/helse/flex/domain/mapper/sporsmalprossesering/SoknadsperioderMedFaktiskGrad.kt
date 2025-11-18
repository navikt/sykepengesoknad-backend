package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.tilSykmeldingstypeDTO
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.SporingType.ORGANISASJONSNUMMER
import no.nav.helse.flex.juridiskvurdering.SporingType.SOKNAD
import no.nav.helse.flex.juridiskvurdering.SporingType.SYKMELDING
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.HVOR_MANGE_TIMER_PER_UKE
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_PROSENT_VERDI
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER_VERDI
import no.nav.helse.flex.soknadsopprettelse.JOBBER_DU_NORMAL_ARBEIDSUKE
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import java.time.LocalDate
import java.util.ArrayList
import kotlin.math.roundToInt

fun hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad: Sykepengesoknad): Pair<List<SoknadsperiodeDTO>, JuridiskVurdering?> {
    val fravar = samleFravaerListe(sykepengesoknad)
    val soknadperioder = sykepengesoknad.soknadPerioder
    val perioder = ArrayList<SoknadsperiodeDTO>()

    val inputSpm = ArrayList<Sporsmal>()
    for (i in soknadperioder!!.indices) {
        val soknadperiode = soknadperioder[i]

        val tag = (
            if (soknadperiode.grad == 100) {
                listOf(
                    JOBBET_DU_100_PROSENT + i,
                    ARBEID_UNDERVEIS_100_PROSENT + i,
                )
            } else {
                listOf(JOBBET_DU_GRADERT + i)
            }
        )

        var avtaltTimer: Double? = null
        var faktiskTimer: Double? = null
        var faktiskGrad: Int? = null

        fun hentSporsmal(): Sporsmal? {
            tag.forEach { tag ->
                val spm = sykepengesoknad.getSporsmalMedTagOrNull(tag)
                spm?.let { return it }
            }
            return null
        }

        val sporsmal = hentSporsmal()
        sporsmal?.let { inputSpm.add(it) }
        if (sporsmal?.forsteSvar == "JA") {
            avtaltTimer =
                sykepengesoknad
                    .getSporsmalMedTagOrNull(HVOR_MANGE_TIMER_PER_UKE + i)
                    ?.forsteSvar
                    ?.replace(',', '.')
                    ?.let { java.lang.Double.parseDouble(it) }

            sykepengesoknad
                .getSporsmalMedTagOrNull(JOBBER_DU_NORMAL_ARBEIDSUKE + i)
                ?.forsteSvar
                ?.let {
                    if (it == "JA") {
                        avtaltTimer = 37.5
                    }
                }

            val gradSporsmal = sykepengesoknad.getSporsmalMedTagOrNull(HVOR_MYE_PROSENT_VERDI + i)

            val faktiskTimerSporsmal = sykepengesoknad.getSporsmalMedTagOrNull(HVOR_MYE_TIMER_VERDI + i)
            val hvorMyeTimer = sykepengesoknad.getSporsmalMedTagOrNull(HVOR_MYE_TIMER + i)
            if (faktiskTimerSporsmal?.svar?.isNotEmpty() == true && hvorMyeTimer?.forsteSvar == "CHECKED") {
                faktiskTimer =
                    faktiskTimerSporsmal
                        .forsteSvar
                        ?.replace(',', '.')
                        ?.let { java.lang.Double.parseDouble(it) }

                val ferieOgPermisjonPerioder =
                    fravar
                        .filter { (_, _, type) -> listOf(FravarstypeDTO.FERIE, FravarstypeDTO.PERMISJON).contains(type) }
                faktiskGrad =
                    beregnFaktiskGrad(
                        faktiskTimer,
                        avtaltTimer,
                        soknadperiode,
                        ferieOgPermisjonPerioder,
                        arbeidGjenopptattDato(sykepengesoknad),
                    )
            } else {
                faktiskGrad =
                    if (gradSporsmal?.forsteSvar == null) {
                        null
                    } else {
                        gradSporsmal.forsteSvar!!
                            .replace(',', '.')
                            .toDouble()
                            .roundToInt()
                    }
            }
        }

        // Det gir ikke verdi å sette faktisk arbeid til mer enn 100 %.
        val kappetFaktiskGrad =
            if (faktiskGrad != null && faktiskGrad > 100) {
                100
            } else if (faktiskGrad != null && faktiskGrad < (100 - soknadperiode.grad)) {
                100 - soknadperiode.grad
            } else {
                faktiskGrad
            }

        perioder.add(
            SoknadsperiodeDTO(
                fom = soknadperiode.fom,
                tom = soknadperiode.tom,
                sykmeldingsgrad = soknadperiode.grad,
                faktiskGrad = kappetFaktiskGrad,
                avtaltTimer = avtaltTimer,
                faktiskTimer = faktiskTimer,
                sykmeldingstype = soknadperiode.sykmeldingstype?.tilSykmeldingstypeDTO(),
                grad = soknadperiode.grad,
            ),
        )
    }
    val juridiskVurdering =
        if (sykepengesoknad.status == Soknadstatus.SENDT) {
            JuridiskVurdering(
                sporing =
                    hashMapOf(
                        SOKNAD to listOf(sykepengesoknad.id),
                    ).also { map ->
                        sykepengesoknad.sykmeldingId?.let {
                            map[SYKMELDING] = listOf(it)
                        }
                        sykepengesoknad.arbeidsgiverOrgnummer?.let {
                            map[ORGANISASJONSNUMMER] = listOf(it)
                        }
                    },
                input =
                    mapOf(
                        "fravar" to fravar,
                        "versjon" to LocalDate.of(2022, 2, 1),
                        "arbeidUnderveis" to inputSpm.map { it.tilSimpeltSporsmal() },
                    ),
                output =
                    mapOf(
                        "perioder" to perioder.map { it.tilSimpelPeriode() },
                        "versjon" to LocalDate.of(2022, 2, 1),
                    ),
                fodselsnummer = sykepengesoknad.fnr,
                lovverk = "folketrygdloven",
                paragraf = "8-13",
                ledd = 1,
                punktum = 2,
                bokstav = null,
                lovverksversjon = LocalDate.of(1997, 5, 1),
                utfall = Utfall.VILKAR_BEREGNET,
            )
        } else {
            null
        }
    return Pair(perioder, juridiskVurdering)
}

data class SimpeltSporsmal(
    val tag: String,
    val svar: List<String>,
    val undersporsmal: List<SimpeltSporsmal>,
)

data class SimpelPeriode(
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val faktiskGrad: Int? = null,
)

private fun SoknadsperiodeDTO.tilSimpelPeriode(): SimpelPeriode =
    SimpelPeriode(
        fom = fom,
        tom = tom,
        faktiskGrad = faktiskGrad,
    )

private fun Sporsmal.tilSimpeltSporsmal(): SimpeltSporsmal =
    SimpeltSporsmal(
        tag = tag,
        svar = svar.map { it.verdi },
        undersporsmal =
            undersporsmal
                .filter { kriterieForVisningAvUndersporsmal?.toString() == forsteSvar }
                .map { it.tilSimpeltSporsmal() },
    )
