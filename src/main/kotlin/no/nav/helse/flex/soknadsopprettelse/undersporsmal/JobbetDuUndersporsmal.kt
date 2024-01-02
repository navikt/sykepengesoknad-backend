package no.nav.helse.flex.soknadsopprettelse.undersporsmal

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.HVOR_MANGE_TIMER_PER_UKE
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_HAR_DU_JOBBET
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_PROSENT
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_PROSENT_VERDI
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER
import no.nav.helse.flex.soknadsopprettelse.HVOR_MYE_TIMER_VERDI
import no.nav.helse.flex.soknadsopprettelse.JOBBER_DU_NORMAL_ARBEIDSUKE
import no.nav.helse.flex.util.DatoUtil
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

fun jobbetDuUndersporsmal(
    periode: Soknadsperiode,
    minProsent: Int,
    index: Int,
    arbeidsgiverNavn: String? = null,
): List<Sporsmal> {
    val periodeTekst =
        DatoUtil.formatterPeriode(
            periode.fom,
            periode.tom,
        )
    val arbeidsgiver = if (arbeidsgiverNavn != null) " hos $arbeidsgiverNavn" else ""
    return listOf(
        Sporsmal(
            tag = HVOR_MYE_HAR_DU_JOBBET + index,
            sporsmalstekst = "Oppgi arbeidsmengde i timer eller prosent:",
            svartype = Svartype.RADIO_GRUPPE_TIMER_PROSENT,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = HVOR_MYE_TIMER + index,
                        sporsmalstekst = "Timer",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal =
                            listOf(
                                Sporsmal(
                                    sporsmalstekst = "Oppgi totalt antall timer du jobbet i perioden $periodeTekst$arbeidsgiver",
                                    undertekst = "Oppgi i timer. Eksempel: 12",
                                    tag = HVOR_MYE_TIMER_VERDI + index,
                                    svartype = Svartype.TIMER,
                                    min = "1",
                                    max =
                                        (150 * ((ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1) / 7.0)).roundToInt()
                                            .toString(),
                                ),
                            ),
                    ),
                    Sporsmal(
                        tag = HVOR_MYE_PROSENT + index,
                        sporsmalstekst = "Prosent",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal =
                            listOf(
                                Sporsmal(
                                    sporsmalstekst =
                                        "Oppgi hvor mange prosent av din normale arbeidstid du " +
                                            "jobbet$arbeidsgiver i perioden $periodeTekst?",
                                    undertekst = "Oppgi i prosent. Eksempel: 40",
                                    tag = HVOR_MYE_PROSENT_VERDI + index,
                                    svartype = Svartype.PROSENT,
                                    min = minProsent.toString(),
                                    max = "99",
                                ),
                            ),
                    ),
                ),
        ),
        Sporsmal(
            tag = JOBBER_DU_NORMAL_ARBEIDSUKE + index,
            sporsmalstekst = "Jobber du vanligvis 37,5 timer i uka$arbeidsgiver?",
            svartype = Svartype.JA_NEI,
            kriterieForVisningAvUndersporsmal = Visningskriterie.NEI,
            undersporsmal =
                listOf(
                    Sporsmal(
                        sporsmalstekst = "Oppgi timer per uke",
                        tag = HVOR_MANGE_TIMER_PER_UKE + index,
                        svartype = Svartype.TIMER,
                        min = "1",
                        max = "150",
                    ),
                ),
        ),
    )
}
