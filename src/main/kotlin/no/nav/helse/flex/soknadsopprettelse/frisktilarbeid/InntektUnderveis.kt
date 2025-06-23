package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.FTA_INNTEKT_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.FTA_INNTEKT_UNDERVEIS_BELOP
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun inntektUnderveis(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    val periodeTekst = DatoUtil.formatterPeriode(fom, tom)
    return Sporsmal(
        tag = FTA_INNTEKT_UNDERVEIS,
        sporsmalstekst = "Hadde du  inntekt i perioden $periodeTekst?",
        undertekst = "Dette kan for eksempel være inntekt fra en annen jobb du ikke har vært sykmeldt fra.",
        svartype = Svartype.JA_NEI,
        // Disse er med for å få muteringen til å fungere
        min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
        max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        svar = emptyList(),
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = FTA_INNTEKT_UNDERVEIS_BELOP,
                    sporsmalstekst = "Hvor mye tjente du i perioden $periodeTekst?",
                    undertekst = "Oppgi beløp før skatt. Har du hatt inntekt i flere jobber skal du oppgi samlet beløp.",
                    svartype = Svartype.BELOP,
                    min = null,
                    max = null,
                    kriterieForVisningAvUndersporsmal = null,
                    svar = emptyList(),
                    undersporsmal = emptyList(),
                ),
            ),
    )
}
