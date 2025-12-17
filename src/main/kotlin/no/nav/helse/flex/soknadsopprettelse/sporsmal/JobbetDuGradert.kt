package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

fun jobbetDuGradert(
    periode: Soknadsperiode,
    arbeidsgiver: String,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst = "I perioden ${
            formatterPeriode(
                periode.fom,
                periode.tom,
            )
        } sier sykmeldingen at du kunne jobbe ${100 - periode.grad} % i jobben din hos $arbeidsgiver. Jobbet du mer enn det?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = jobbetDuGradertUndersporsmal(periode, 100 + 1 - periode.grad, index),
    )

fun jobbetDuGradertUndersporsmal(
    periode: Soknadsperiode,
    minProsent: Int,
    index: Int,
): List<Sporsmal> {
    val periodeTekst =
        formatterPeriode(
            periode.fom,
            periode.tom,
        )
    return listOf(
        Sporsmal(
            tag = HVOR_MANGE_TIMER_PER_UKE + index,
            sporsmalstekst = "Hvor mange timer i uken jobber du vanligvis når du er frisk? Varierer det, kan du oppgi gjennomsnittet.",
            svartype = Svartype.TALL,
            min = "1",
            max = "150",
        ),
        Sporsmal(
            tag = HVOR_MYE_HAR_DU_JOBBET + index,
            sporsmalstekst = "Hvor mye jobbet du tilsammen $periodeTekst?",
            svartype = Svartype.RADIO_GRUPPE_TIMER_PROSENT,
            undertekst = "Velg timer eller prosent",
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
                                    tag = HVOR_MYE_TIMER_VERDI + index,
                                    svartype = Svartype.TIMER,
                                    min = "1",
                                    max =
                                        (150 * ((ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1) / 7.0))
                                            .roundToInt()
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
                                    tag = HVOR_MYE_PROSENT_VERDI + index,
                                    svartype = Svartype.PROSENT,
                                    min = minProsent.toString(),
                                    max = "99",
                                ),
                            ),
                    ),
                ),
        ),
    )
}
