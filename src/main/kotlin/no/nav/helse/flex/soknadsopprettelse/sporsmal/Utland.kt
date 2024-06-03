package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS_NAR
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun oppholdUtenforEOSSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = OPPHOLD_UTENFOR_EOS,
        sporsmalstekst = "Var du på reise utenfor EØS mens du var sykmeldt ${DatoUtil.formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = OPPHOLD_UTENFOR_EOS_NAR,
                    sporsmalstekst = "Når var du utenfor EØS?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
