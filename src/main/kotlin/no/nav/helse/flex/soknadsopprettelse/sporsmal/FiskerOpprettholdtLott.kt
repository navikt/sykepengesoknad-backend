package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.FISKER_LOTT_OPPRETTHOLDT_INNTEKT
import no.nav.helse.flex.soknadsopprettelse.FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT
import no.nav.helse.flex.util.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun fiskerLottOpprettholdtInntekt100Prosent(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = FISKER_LOTT_OPPRETTHOLDT_INNTEKT,
        sporsmalstekst = "Hadde du lottinntekt selv om du var 100 % sykmeldt og ikke jobbet selv?",
        svartype = Svartype.JA_NEI,
        min = fom.format(ISO_LOCAL_DATE),
        max = tom.format(ISO_LOCAL_DATE),
    )

fun fiskerLottOpprettholdtInntektGradert(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT,
        sporsmalstekst =
            "Hadde du lottinntekt mens du var sykmeldt ${formatterPeriode(fom, tom)}, " +
                "som ikke var et resultat av at du selv jobbet?",
        svartype = Svartype.JA_NEI,
        min = fom.format(ISO_LOCAL_DATE),
        max = tom.format(ISO_LOCAL_DATE),
    )
