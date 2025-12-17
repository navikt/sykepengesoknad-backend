package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

fun jobbetDu100ProsentArbeidstaker(
    periode: Soknadsperiode,
    arbeidsgiver: String,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst =
            byggSporsmalstekstMedPeriode(
                periode.fom,
                periode.tom,
                "var du 100 % sykmeldt fra $arbeidsgiver. Jobbet du noe hos $arbeidsgiver i denne perioden?",
            ),
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            jobbetDuUndersporsmal(
                periode = periode,
                minProsent = 1,
                index = index,
                arbeidsgiverNavn = arbeidsgiver,
            ),
    )

fun jobbetDuGradertArbeidstaker(
    periode: Soknadsperiode,
    arbeidsgiver: String,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst =
            byggSporsmalstekstMedPeriode(
                periode.fom,
                periode.tom,
                "sier sykmeldingen at du kunne jobbe ${100 - periode.grad} % i jobben din hos $arbeidsgiver. Jobbet du mer enn det?",
            ),
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuGradertUndersporsmal(periode, 100 + 1 - periode.grad, index),
    )

fun jobbetDu100ProsentSelvstendigFrilanser(
    periode: Soknadsperiode,
    arbeidssituasjon: Arbeidssituasjon,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = ARBEID_UNDERVEIS_100_PROSENT + index,
        sporsmalstekst =
            byggSporsmalstekstMedPeriode(
                periode.fom,
                periode.tom,
                "var du 100% sykmeldt som $arbeidssituasjon. Jobbet du noe i denne perioden?",
            ),
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuUndersporsmal(periode, 1, index),
    )

fun jobbetDuGradertSelvstendigFrilanser(
    periode: Soknadsperiode,
    arbeidssituasjon: Arbeidssituasjon,
    index: Int,
): Sporsmal =
    Sporsmal(
        tag = JOBBET_DU_GRADERT + index,
        sporsmalstekst =
            byggSporsmalstekstMedPeriode(
                periode.fom,
                periode.tom,
                "sier sykmeldingen at du kunne jobbe ${100 - periode.grad} % som $arbeidssituasjon. Jobbet du mer enn det?",
            ),
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal = jobbetDuGradertUndersporsmal(periode, 100 + 1 - periode.grad, index),
    )

private fun jobbetDuGradertUndersporsmal(
    periode: Soknadsperiode,
    minProsent: Int,
    index: Int,
): List<Sporsmal> =
    listOf(
        Sporsmal(
            tag = HVOR_MANGE_TIMER_PER_UKE + index,
            sporsmalstekst = "Hvor mange timer i uken jobber du vanligvis når du er frisk? Varierer det, kan du oppgi gjennomsnittet.",
            svartype = Svartype.TALL,
            min = "1",
            max = "150",
        ),
        Sporsmal(
            tag = HVOR_MYE_HAR_DU_JOBBET + index,
            sporsmalstekst =
                byggSporsmalstekstMedPeriodeMidt(
                    "Hvor mye jobbet du tilsammen",
                    periode.fom,
                    periode.tom,
                    "?",
                ),
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

private fun jobbetDuUndersporsmal(
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
                                    undertekst = "Eksempel: 8,5",
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
                        undertekst = "Eksempel: 8,5",
                        tag = HVOR_MANGE_TIMER_PER_UKE + index,
                        svartype = Svartype.TIMER,
                        min = "1",
                        max = "150",
                    ),
                ),
        ),
    )
}
