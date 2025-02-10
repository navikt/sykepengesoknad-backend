package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate

fun inntektUnderveis(fom: LocalDate, tom: LocalDate): Sporsmal {
    val periodeTekst = DatoUtil.formatterPeriode(fom, tom)
    return Sporsmal(
        tag = "FTA_INNTEKT_UNDERVEIS",
        sporsmalstekst = "Hadde du  inntekt i perioden $periodeTekst?",
        undertekst = "Dette kan for eksempel være inntekt fra en annen jobb du ikke er sykmeldt fra.",
        svartype = Svartype.JA_NEI,
        min = null,
        max = null,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        svar = emptyList(),
        undersporsmal = listOf(
            Sporsmal(
                tag = "FTA_INNTEKT_UNDERVEIS_MER_ENN_PLEIER",
                sporsmalstekst = "Tjente du mer enn du pleier i perioden $periodeTekst?",
                undertekst = null,
                svartype = Svartype.JA_NEI,
                min = null,
                max = null,
                kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
                svar = emptyList(),
                undersporsmal = listOf(
                    Sporsmal(
                        tag = "FTA_INNTEKT_UNDERVEIS_MER_ENN_PLEIER_BELOP",
                        sporsmalstekst = "Hvor mye tjente du, utover det du pleier?",
                        undertekst = "Har du tjent mer i flere jobber skal du oppgi samlet beløp, før skatt",
                        svartype = Svartype.BELOP,
                        min = null,
                        max = null,
                        kriterieForVisningAvUndersporsmal = null,
                        svar = emptyList(),
                        undersporsmal = emptyList()
                    )
                )
            )
        )
    )
}
