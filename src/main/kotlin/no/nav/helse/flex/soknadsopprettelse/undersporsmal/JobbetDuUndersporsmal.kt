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
import no.nav.helse.flex.util.DatoUtil
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

fun jobbetDuUndersporsmal(
    periode: Soknadsperiode,
    minProsent: Int,
    index: Int
): List<Sporsmal> {
    val periodeTekst = DatoUtil.formatterPeriode(
        periode.fom,
        periode.tom
    )
    return listOf(
        Sporsmal(
            tag = HVOR_MANGE_TIMER_PER_UKE + index,
            sporsmalstekst = "Hvor mange timer i uken jobber du vanligvis når du er frisk? Varierer det, kan du oppgi gjennomsnittet.",
            undertekst = "timer per uke",
            svartype = Svartype.TALL,
            min = "1",
            max = "150"
        ),
        Sporsmal(
            tag = HVOR_MYE_HAR_DU_JOBBET + index,
            sporsmalstekst = "Hvor mye jobbet du tilsammen $periodeTekst?",
            svartype = Svartype.RADIO_GRUPPE_TIMER_PROSENT,
            undertekst = "Velg timer eller prosent",
            undersporsmal = listOf(
                Sporsmal(
                    tag = HVOR_MYE_TIMER + index,
                    sporsmalstekst = "Timer",
                    svartype = Svartype.RADIO,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal = listOf(
                        Sporsmal(
                            tag = HVOR_MYE_TIMER_VERDI + index,
                            undertekst = "timer totalt",
                            svartype = Svartype.TALL,
                            min = "1",
                            max = (150 * ((ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1) / 7.0)).roundToInt()
                                .toString()
                        )
                    )
                ),
                Sporsmal(
                    tag = HVOR_MYE_PROSENT + index,
                    sporsmalstekst = "Prosent",
                    svartype = Svartype.RADIO,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal = listOf(
                        Sporsmal(
                            tag = HVOR_MYE_PROSENT_VERDI + index,
                            undertekst = "prosent",
                            svartype = Svartype.TALL,
                            min = minProsent.toString(),
                            max = "99"
                        )
                    )
                ),
            )
        )
    )
}
