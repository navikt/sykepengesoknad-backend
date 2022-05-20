package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import no.nav.syfo.soknadsopprettelse.FULLTIDSSTUDIUM
import no.nav.syfo.soknadsopprettelse.UTDANNING
import no.nav.syfo.soknadsopprettelse.UTDANNING_START
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun utdanningsSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal =
    Sporsmal(
        tag = UTDANNING,
        sporsmalstekst = "Har du vært under utdanning i løpet av perioden ${DatoUtil.formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTDANNING_START,
                sporsmalstekst = "Når startet du på utdanningen?",
                svartype = Svartype.DATO,
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = FULLTIDSSTUDIUM,
                sporsmalstekst = "Er utdanningen et fulltidsstudium?",
                svartype = Svartype.JA_NEI
            )
        )
    )
