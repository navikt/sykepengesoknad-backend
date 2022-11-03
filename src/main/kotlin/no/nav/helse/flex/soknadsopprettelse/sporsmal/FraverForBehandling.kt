package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.EGENMELDINGER_NAR
import no.nav.helse.flex.soknadsopprettelse.FRAVER_FOR_BEHANDLING
import no.nav.helse.flex.soknadsopprettelse.PAPIRSYKMELDING_NAR
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_EGENMELDING
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_PAPIRSYKMELDING
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_SYK
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun fraverForBehandling(soknadMetadata: Sykepengesoknad, tidligsteFomForSykmelding: LocalDate): Sporsmal {
    return Sporsmal(
        tag = FRAVER_FOR_BEHANDLING,
        sporsmalstekst = "Vi ser at sykmeldingen inneholder behandlingsdager mellom ${DatoUtil.formatterPeriode(tidligsteFomForSykmelding, soknadMetadata.tom!!)}. Var du syk og borte fra jobb før dette, nærmere bestemt ${DatoUtil.formatterPeriode(tidligsteFomForSykmelding.minusDays(16), tidligsteFomForSykmelding.minusDays(1))}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = TIDLIGERE_SYK,
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = TIDLIGERE_EGENMELDING,
                        sporsmalstekst = "Jeg var syk med egenmelding",
                        svartype = Svartype.CHECKBOX,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = EGENMELDINGER_NAR,
                                sporsmalstekst = "Hvilke dager var du syk med egenmelding? Du trenger bare oppgi dager før ${DatoUtil.formatterDato(tidligsteFomForSykmelding)}.",
                                svartype = Svartype.PERIODER,
                                min = tidligsteFomForSykmelding.minusMonths(6).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = tidligsteFomForSykmelding.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    ),
                    Sporsmal(
                        tag = TIDLIGERE_PAPIRSYKMELDING,
                        sporsmalstekst = "Jeg var syk med papirsykmelding",
                        svartype = Svartype.CHECKBOX,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = PAPIRSYKMELDING_NAR,
                                sporsmalstekst = "Hvilke dager var du syk med papirsykmelding? Du trenger bare oppgi dager før ${DatoUtil.formatterDato(tidligsteFomForSykmelding)}.",
                                svartype = Svartype.PERIODER,
                                min = tidligsteFomForSykmelding.minusMonths(6).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = tidligsteFomForSykmelding.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    )
                )
            )
        )
    )
}
