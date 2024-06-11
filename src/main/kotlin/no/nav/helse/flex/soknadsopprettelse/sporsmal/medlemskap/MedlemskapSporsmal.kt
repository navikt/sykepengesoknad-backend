package no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun lagSporsmalOmOppholdstillatelse(tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE,
        sporsmalstekst = "Har du oppholdstillatelse fra Utlendingsdirektoratet?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                    sporsmalstekst = "Hvilken dato fikk du denne oppholdstillatelsen?",
                    svartype = Svartype.DATO,
                    // Vi vet ikke hvor lang tid tilbake en oppholdstillatelse kan ha bli gitt så vi setter 10 år i
                    // samarbeid med LovMe.
                    min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                    // Vi vet at en vedtaksdato ikke kan være i fremtiden så vi setter dagens dato som maks.
                    max = tom.format(ISO_LOCAL_DATE),
                ),
                Sporsmal(
                    tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE,
                    sporsmalstekst = "Er oppholdstillatelsen midlertidig eller permanent?",
                    svartype = Svartype.RADIO_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG,
                                sporsmalstekst = "Midlertidig",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG_PERIODE,
                                            sporsmalstekst = "Periode for oppholdstillatelse",
                                            svartype = Svartype.PERIODE,
                                            min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                                            max = tom.plusYears(10).format(ISO_LOCAL_DATE),
                                        ),
                                    ),
                            ),
                            Sporsmal(
                                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT,
                                sporsmalstekst = "Permanent",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT_DATO,
                                            sporsmalstekst = "Fra og med",
                                            svartype = Svartype.DATO,
                                            min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                                            max = tom.format(ISO_LOCAL_DATE),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
    )
}

fun lagSporsmalOmArbeidUtenforNorge(tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
        sporsmalstekst = "Har du arbeidet utenfor Norge i løpet av de siste 12 månedene før du ble syk?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(0, tom),
            ),
    )
}

fun medIndex(
    tekst: String,
    index: Int,
): String {
    return "$tekst$index"
}

fun lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(
    index: Int,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, index),
                    sporsmalstekst = "I hvilket land arbeidet du?",
                    svartype = Svartype.COMBOBOX_SINGLE,
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, index),
                    sporsmalstekst = "Hvilken arbeidsgiver jobbet du for?",
                    svartype = Svartype.FRITEKST,
                    min = "1",
                    // Noen arbeidsgivere kan ha ganske lange navn.
                    max = "200",
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, index),
                    sporsmalstekst = "I hvilken periode arbeidet du i utlandet?",
                    svartype = Svartype.PERIODE,
                    // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                    min = tom.minusYears(10).format(ISO_LOCAL_DATE),
                    max = tom.plusYears(1).format(ISO_LOCAL_DATE),
                ),
            ),
    )
}

fun lagSporsmalOmOppholdUtenforNorge(tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
        sporsmalstekst = "Har du oppholdt deg i utlandet i løpet av de siste 12 månedene før du ble syk?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforNorge(0, tom),
            ),
    )
}

fun lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforNorge(
    index: Int,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_HVOR, index),
                    sporsmalstekst = "I hvilket land utenfor Norge har du oppholdt deg?",
                    svartype = Svartype.COMBOBOX_SINGLE,
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE, index),
                    sporsmalstekst = "Hva gjorde du i utlandet?",
                    svartype = Svartype.RADIO_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_STUDIE, index),
                                sporsmalstekst = "Jeg studerte",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FERIE, index),
                                sporsmalstekst = "Jeg var på ferie",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_BO, index),
                                sporsmalstekst = "Jeg bodde der",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_EKTEFELLE, index),
                                sporsmalstekst = "Jeg var med ektefelle/samboer som jobbet der",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_ANNET, index),
                                sporsmalstekst = "Annet",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_ANNET_FRITEKST, index),
                                            sporsmalstekst = "Beskriv hva du gjorde",
                                            svartype = Svartype.FRITEKST,
                                            min = "1",
                                            max = "200",
                                        ),
                                    ),
                            ),
                        ),
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR, index),
                    sporsmalstekst = "I hvilken periode oppholdt du deg i dette landet?",
                    svartype = Svartype.PERIODE,
                    // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                    min = tom.minusYears(2).format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}

fun lagSporsmalOmOppholdUtenforEos(tom: LocalDate): Sporsmal {
    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
        sporsmalstekst = "Har du oppholdt deg utenfor EU/EØS eller Sveits i løpet av de siste 12 månedene før du ble syk?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforEos(0, tom),
            ),
    )
}

fun lagGruppertUndersporsmalTilSporsmalOmOppholdUtenforEos(
    index: Int,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_GRUPPERING, index),
        svartype = Svartype.GRUPPE_AV_UNDERSPORSMAL,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_HVOR, index),
                    sporsmalstekst = "I hvilket land utenfor EU/EØS eller Sveits har du oppholdt deg?",
                    svartype = Svartype.COMBOBOX_SINGLE,
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE, index),
                    sporsmalstekst = "Hva gjorde du i utlandet?",
                    svartype = Svartype.RADIO_GRUPPE,
                    undersporsmal =
                        listOf(
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_STUDIE, index),
                                sporsmalstekst = "Jeg studerte",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_FERIE, index),
                                sporsmalstekst = "Jeg var på ferie",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_BO, index),
                                sporsmalstekst = "Jeg bodde der",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_EKTEFELLE, index),
                                sporsmalstekst = "Jeg var med ektefelle/samboer som jobbet der",
                                svartype = Svartype.RADIO,
                            ),
                            Sporsmal(
                                tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET, index),
                                sporsmalstekst = "Annet",
                                svartype = Svartype.RADIO,
                                kriterieForVisningAvUndersporsmal = Visningskriterie.CHECKED,
                                undersporsmal =
                                    listOf(
                                        Sporsmal(
                                            tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET_FRITEKST, index),
                                            sporsmalstekst = "Beskriv hva du gjorde",
                                            svartype = Svartype.FRITEKST,
                                            min = "1",
                                            max = "200",
                                        ),
                                    ),
                            ),
                        ),
                ),
                Sporsmal(
                    tag = medIndex(MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR, index),
                    sporsmalstekst = "I hvilken periode oppholdt du deg i dette landet?",
                    svartype = Svartype.PERIODE,
                    // Til- og fra-dato er satt statisk i samarbeid med LovMe.
                    min = tom.minusYears(2).format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}
