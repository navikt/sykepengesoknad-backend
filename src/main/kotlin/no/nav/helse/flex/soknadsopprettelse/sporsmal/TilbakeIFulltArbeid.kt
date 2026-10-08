package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.DATO
import no.nav.helse.flex.domain.Svartype.JA_NEI
import no.nav.helse.flex.domain.Visningskriterie.JA
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.util.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun tilbakeIFulltArbeidSporsmal(
    arbeidsgiverNavn: String?,
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal {
    val sporsmalsTekst =
        if (arbeidsgiverNavn != null) {
            "Var du tilbake i fullt arbeid hos $arbeidsgiverNavn i løpet av perioden ${formatterPeriode(fom, tom)}?"
        } else {
            "Var du tilbake i fullt arbeid i løpet av perioden ${formatterPeriode(fom, tom)}?"
        }

    return Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = sporsmalsTekst,
        svartype = JA_NEI,
        kriterieForVisningAvUndersporsmal = JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TILBAKE_NAR,
                    sporsmalstekst = "Når begynte du å jobbe igjen?",
                    svartype = DATO,
                    min = fom.format(ISO_LOCAL_DATE),
                    max = tom.format(ISO_LOCAL_DATE),
                ),
            ),
    )
}
