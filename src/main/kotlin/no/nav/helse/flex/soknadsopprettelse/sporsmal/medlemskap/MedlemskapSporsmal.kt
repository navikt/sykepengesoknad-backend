package no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SettOppSoknadOpts
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagMedlemskapOppholdstillatelseSporsmal(options: SettOppSoknadOpts): Sporsmal {
    val (sykepengesoknad) = options
    val tilDato = sykepengesoknad.tom ?: LocalDate.now()
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
                svartype = Svartype.DATO
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
