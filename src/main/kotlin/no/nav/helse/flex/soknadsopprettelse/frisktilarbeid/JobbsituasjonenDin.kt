package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

private fun fortsattArbeidssokerDato(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER_AVREGISTRERT_NAR,
        sporsmalstekst = "Fra og med når?",
        undertekst = "Du vil ikke være friskmeldt til arbeidsformidling fra og med denne datoen",
        svartype = Svartype.DATO,
        min = fom.format(ISO_LOCAL_DATE),
        max = tom.format(ISO_LOCAL_DATE),
        kriterieForVisningAvUndersporsmal = null,
        undersporsmal = emptyList(),
    )
}

private fun fortsattArbeidssoker(
    nyJobbUndersporsmal: Boolean,
    medDatoSporsmal: Boolean,
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    val tag =
        if (nyJobbUndersporsmal) FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER_NY_JOBB else FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER
    return Sporsmal(
        tag = tag,
        sporsmalstekst = "Vil du fortsatt være registrert som arbeidssøker hos Nav?",
        undertekst =
            if (nyJobbUndersporsmal) {
                "Svar ja hvis du har begynt i en midlertidig jobb og fortsatt søker andre jobber"
            } else {
                null
            },
        svartype = Svartype.JA_NEI,
        min = null,
        max = null,
        kriterieForVisningAvUndersporsmal = if (medDatoSporsmal) Visningskriterie.NEI else null,
        undersporsmal = if (medDatoSporsmal) listOf(fortsattArbeidssokerDato(fom, tom)) else emptyList(),
    )
}

fun jobbsituasjonenDin(
    fom: LocalDate,
    tom: LocalDate,
    sisteSoknad: Boolean = false,
): Sporsmal {
    val periodeTekst = DatoUtil.formatterPeriode(fom, tom)
    return Sporsmal(
        tag = FTA_JOBBSITUASJONEN_DIN,
        sporsmalstekst = "Begynte du i ny jobb i perioden $periodeTekst?",
        undertekst = null,
        svartype = Svartype.RADIO_GRUPPE,
        min = null,
        max = null,
        kriterieForVisningAvUndersporsmal = null,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = FTA_JOBBSITUASJONEN_DIN_JA,
                    sporsmalstekst = "Ja",
                    undertekst = null,
                    svartype = Svartype.RADIO,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal =
                        mutableListOf(
                            Sporsmal(
                                tag = FTA_JOBBSITUASJONEN_DIN_NAR,
                                sporsmalstekst = "Når begynte du i ny jobb?",
                                undertekst = null,
                                svartype = Svartype.DATO,
                                min = fom.format(ISO_LOCAL_DATE),
                                max = tom.format(ISO_LOCAL_DATE),
                                kriterieForVisningAvUndersporsmal = null,
                                undersporsmal = emptyList(),
                            ),
                        ).also {
                            if (!sisteSoknad) {
                                it.add(
                                    fortsattArbeidssoker(
                                        nyJobbUndersporsmal = true,
                                        medDatoSporsmal = false,
                                        fom = fom,
                                        tom = tom,
                                    ),
                                )
                            }
                        },
                ),
                Sporsmal(
                    tag = FTA_JOBBSITUASJONEN_DIN_NEI,
                    sporsmalstekst = "Nei",
                    undertekst = null,
                    svartype = Svartype.RADIO,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                    undersporsmal =
                        mutableListOf<Sporsmal>().also {
                            if (!sisteSoknad) {
                                it.add(
                                    fortsattArbeidssoker(
                                        nyJobbUndersporsmal = false,
                                        medDatoSporsmal = true,
                                        fom = fom,
                                        tom = tom,
                                    ),
                                )
                            }
                        },
                ),
            ),
    )
}
