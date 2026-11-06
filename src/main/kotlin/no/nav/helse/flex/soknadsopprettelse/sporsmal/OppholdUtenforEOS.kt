package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.util.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun oppholdUtenforEOSSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = OPPHOLD_UTENFOR_EOS,
        sporsmalstekst = "Var du på reise utenfor EU/EØS og Storbritannia mens du var sykmeldt ${formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = OPPHOLD_UTENFOR_EOS_NAR,
                    sporsmalstekst = "Når var du på reise?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
