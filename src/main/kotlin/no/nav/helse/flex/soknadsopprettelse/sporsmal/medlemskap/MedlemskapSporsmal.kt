package no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_PERIODE
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagMedlemskapOppholdstillatelseSporsmal(tilDato: LocalDate): Sporsmal {
    // TODO: Sjekk opp datoer og perioder med LovMe.
    val fraDato = tilDato.minusMonths(12)

    return Sporsmal(
        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE,
        sporsmalstekst = "Har du oppholdstillatelse fra utlendingsdirektoratet?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO,
                sporsmalstekst = "Oppgi vedtaksdato om oppholdstillatelse:",
                svartype = Svartype.DATO,
                min = fraDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tilDato.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT,
                sporsmalstekst = "Har du fått permanent oppholdstillatelse?",
                svartype = Svartype.JA_NEI,
                kriterieForVisningAvUndersporsmal = Visningskriterie.NEI,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE,
                        sporsmalstekst = "Hvilken periode har du fått oppholdstillatelse?",
                        svartype = Svartype.PERIODER,
                        min = fraDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        max = tilDato.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    )
                )
            )
        )
    )
}

fun lagMedlemskapArbeidUtenforNorgeSporsmal(tilDato: LocalDate): Sporsmal {
    val fraDato = tilDato.minusMonths(12)

    return Sporsmal(
        tag = MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
        sporsmalstekst = "Har du utført arbeid utenfor Norge i det siste 12 månedene?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        // Holder en liste med IKKE_RELEVANT som fungerer som en container for underspørsmålene sånn at vi kan legge
        // til flere perioder med opphold utland.
        undersporsmal = listOf(
            lagUndersporsmalTilArbeidUtenforNorgeSporsmal(0, fraDato, tilDato)
        )
    )
}

fun medIndex(tekst: String, index: Int): String {
    return tekst.plus("_").plus(index)
}

fun lagUndersporsmalTilArbeidUtenforNorgeSporsmal(
    index: Int,
    // TODO: Rydd i datoer og perioder med LovMe.
    fraDato: LocalDate = LocalDate.now().minusMonths(12),
    tilDato: LocalDate = LocalDate.now()
): Sporsmal {
    return Sporsmal(
        tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_PERIODE, index),
        svartype = Svartype.IKKE_RELEVANT,
        undersporsmal = listOf(
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER, index),
                sporsmalstekst = "Arbeidsgiver",
                svartype = Svartype.FRITEKST
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR, index),
                sporsmalstekst = "Velg land",
                svartype = Svartype.COMBOBOX_SINGLE
            ),
            Sporsmal(
                tag = medIndex(MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR, index),
                svartype = Svartype.PERIODER,
                min = fraDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tilDato.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        )
    )
}
