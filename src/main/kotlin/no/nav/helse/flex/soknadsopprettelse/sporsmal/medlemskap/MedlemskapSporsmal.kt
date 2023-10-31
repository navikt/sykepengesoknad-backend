package no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagSporsmalOmOppholdstillatelse(): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE,
        sporsmalstekst = "Har du oppholdstillatelse fra utlendingsdirektoratet?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                sporsmalstekst = "Hvilken dato fikk du denne oppholdstillatelsen?",
                svartype = Svartype.DATO,
                // Vi vet ikke hvor lang tid tilbake en oppholdstillatelse kan ha bli gitt så vi setter 10 år i
                // samarbeid med LovMe.
                min = LocalDate.now().minusYears(10).format(DateTimeFormatter.ISO_LOCAL_DATE),
                // Vi vet at en vedtaksdato ikke kan være i fremtiden så vi setter dagens dato som maks.
                max = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE,
                sporsmalstekst = "Er oppholdstillatelsen midlertidig eller permanent?",
                svartype = Svartype.RADIO_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG,
                        sporsmalstekst = "Midlertidig",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG_PERIODE,
                                svartype = Svartype.PERIODE,
                                min = LocalDate.now().minusYears(10).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = LocalDate.now().plusYears(10).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    ),
                    Sporsmal(
                        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT,
                        sporsmalstekst = "Permanent",
                        svartype = Svartype.RADIO,
                        kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT_DATO,
                                sporsmalstekst = "Fra og med",
                                svartype = Svartype.DATO,
                                min = LocalDate.now().minusYears(10).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                max = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                            )
                        )
                    )
                )
            )
        )
    )
}

fun lagSporsmalOmArbeidUtenforNorge(): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
        sporsmalstekst = "Har du arbeidet utenfor Norge i løpet av de siste 12 månedene før du ble syk?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(0)
        )
    )
}

fun medIndex(tekst: String, index: Int): String {
    return "$tekst$index"
}

fun lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(index: Int): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal = listOf(
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, index),
                sporsmalstekst = "I hvilket land arbeidet du?",
                svartype = Svartype.COMBOBOX_SINGLE
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, index),
                sporsmalstekst = "Hvilken arbeidsgiver jobbet du for?",
                svartype = Svartype.FRITEKST,
                min = "1",
                // Noen arbeidsgivere kan ha ganske lange navn.
                max = "200"
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, index),
                sporsmalstekst = "I hvilken periode arbeidet du i utlandet?",
                svartype = Svartype.PERIODE,
                // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                min = LocalDate.now().minusYears(10).format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}

fun lagSporsmalOmOppholdUtenforNorge(): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
        sporsmalstekst = "Har du oppholdt deg utenfor Norge i løpet av de siste 12 månedene?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforNorge(0)
        )
    )
}

fun lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforNorge(index: Int): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal = listOf(
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_HVOR, index),
                sporsmalstekst = "I hvilket land utenfor Norge har du oppholdt deg?",
                svartype = Svartype.COMBOBOX_SINGLE
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE, index),
                sporsmalstekst = "Hva var årsaken til oppholdet?",
                svartype = Svartype.RADIO_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_STUDIE, index),
                        sporsmalstekst = "Studier",
                        svartype = Svartype.RADIO
                    ),
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FERIE, index),
                        sporsmalstekst = "Ferie",
                        svartype = Svartype.RADIO
                    ),
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FORSORG, index),
                        sporsmalstekst = "Forsørget medfølgende familiemedlem",
                        svartype = Svartype.RADIO
                    )
                )
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR, index),
                sporsmalstekst = "I hvilken periode oppholdt du deg i dette landet?",
                svartype = Svartype.PERIODE,
                // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                min = LocalDate.now().minusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}

fun lagSporsmalOmOppholdUtenforEos(): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
        sporsmalstekst = "Har du oppholdt deg utenfor EØS i løpet av de siste 12 månedene?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforEos(0)
        )
    )
}

fun lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforEos(index: Int): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal = listOf(
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_HVOR, index),
                sporsmalstekst = "I hvilket land utenfor EØS har du oppholdt deg?",
                svartype = Svartype.COMBOBOX_SINGLE
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE, index),
                sporsmalstekst = "Hva var årsaken til oppholdet?",
                svartype = Svartype.RADIO_GRUPPE,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_STUDIE, index),
                        sporsmalstekst = "Studier",
                        svartype = Svartype.RADIO
                    ),
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_FERIE, index),
                        sporsmalstekst = "Ferie",
                        svartype = Svartype.RADIO
                    ),
                    Sporsmal(
                        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_FORSORG, index),
                        sporsmalstekst = "Forsørget medfølgende familiemedlem",
                        svartype = Svartype.RADIO
                    )
                )
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR, index),
                sporsmalstekst = "I hvilken periode oppholdt du deg i dette landet?",
                svartype = Svartype.PERIODE,
                // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                min = LocalDate.now().minusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}
