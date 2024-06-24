package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.soknadsopprettelse.PERIODER
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun oppholdUtenforEOSSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = OPPHOLD_UTENFOR_EOS,
        sporsmalstekst = "Var du på reise utenfor EU/EØS mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = OPPHOLD_UTENFOR_EOS_NAR,
                    sporsmalstekst = "Når var du utenfor EU/EØS?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}

fun gammeltUtenlandsoppholdArbeidsledigAnnetSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = ARBEIDSLEDIG_UTLAND,
        sporsmalstekst = "Var du på reise utenfor EU/EØS mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = UTLAND_NAR,
                    sporsmalstekst = "Når var du utenfor EU/EØS?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
                Sporsmal(
                    tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
                    sporsmalstekst = "Har du søkt om å beholde sykepengene for disse dagene?",
                    svartype = Svartype.JA_NEI,
                    undersporsmal = emptyList(),
                ),
            ),
    )
}

fun gammeltUtenlandsoppholdSelvstendigSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = UTLAND,
        sporsmalstekst = "Har du vært utenfor EU/EØS mens du var sykmeldt " + formatterPeriode(fom, tom) + "?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = PERIODER,
                    sporsmalstekst = "Når var du utenfor EU/EØS?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
                Sporsmal(
                    tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
                    sporsmalstekst = "Har du søkt om å beholde sykepengene for disse dagene?",
                    svartype = Svartype.JA_NEI,
                    undersporsmal = emptyList(),
                ),
            ),
    )
}

fun gammeltUtenlandsoppholdArbeidstakerSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    return Sporsmal(
        tag = UTLAND_V2,
        sporsmalstekst = "Var du på reise utenfor EU/EØS mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = UTLAND_NAR_V2,
                    sporsmalstekst = "Når var du utenfor EU/EØS?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}
