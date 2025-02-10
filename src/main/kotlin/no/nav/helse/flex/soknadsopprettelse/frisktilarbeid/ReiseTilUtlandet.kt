package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun ftaReiseTilUtlandet(fom: LocalDate, tom: LocalDate): Sporsmal {
    val periodeTekst = DatoUtil.formatterPeriode(fom, tom)
    return Sporsmal(
        tag = "FTA_REISE_TIL_UTLANDET",
        sporsmalstekst = "Var du på reise utenfor EU/EØS i perioden $periodeTekst?",
        undertekst = null,
        svartype = Svartype.JA_NEI,
        min = null,
        max = null,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        svar = emptyList(),
        undersporsmal = listOf(
            Sporsmal(
                tag = "FTA_REISE_TIL_UTLANDET_NAR",
                sporsmalstekst = "Når var du utenfor EU/EØS?",
                undertekst = null,
                svartype = Svartype.PERIODER,
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = emptyList()
            )
        )
    )
}
