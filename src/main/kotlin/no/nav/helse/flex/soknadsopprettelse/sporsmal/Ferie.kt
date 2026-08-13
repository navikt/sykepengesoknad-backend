package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.util.formatterPeriode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun ferieSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = FERIE_V2,
        sporsmalstekst = "Tok du ut feriedager i tidsrommet ${formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = FERIE_NAR_V2,
                    sporsmalstekst = "Når tok du ut feriedager?",
                    svartype = Svartype.PERIODER,
                    min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
