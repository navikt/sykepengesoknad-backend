package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.util.formatterDatoUtenÅr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun tilbakeIFulltArbeidGradertReisetilskuddSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = TILBAKE_I_ARBEID,
        sporsmalstekst = "Var du tilbake i ditt vanlige arbeid uten ekstra reiseutgifter før ${
            formatterDatoUtenÅr(tom.plusDays(1))
        }?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = TILBAKE_NAR,
                    sporsmalstekst = "Når var du tilbake?",
                    svartype = Svartype.DATO,
                    min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
