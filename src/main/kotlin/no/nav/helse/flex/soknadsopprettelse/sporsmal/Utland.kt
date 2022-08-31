package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun utenlandsoppholdArbeidsledigAnnetSporsmal(fom: LocalDate, tom: LocalDate): Sporsmal =
    Sporsmal(
        tag = ARBEIDSLEDIG_UTLAND,
        sporsmalstekst = "Var du på reise utenfor EU/EØS/Storbritannia mens du var sykmeldt ${DatoUtil.formatterPeriode(fom, tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = UTLAND_NAR,
                sporsmalstekst = "Når var du utenfor EU/EØS/Storbritannia?",
                svartype = Svartype.PERIODER,
                min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ),
            Sporsmal(
                tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
                sporsmalstekst = "Har du søkt om å beholde sykepengene for disse dagene?",
                svartype = Svartype.JA_NEI,
                undersporsmal = emptyList()
            )
        )
    )
