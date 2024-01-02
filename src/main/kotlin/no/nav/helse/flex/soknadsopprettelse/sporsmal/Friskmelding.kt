package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import no.nav.helse.flex.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun friskmeldingSporsmal(
    fom: LocalDate,
    tom: LocalDate,
): Sporsmal =
    Sporsmal(
        tag = FRISKMELDT,
        sporsmalstekst = "Brukte du hele sykmeldingen fram til ${DatoUtil.formatterDato(tom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.NEI,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = FRISKMELDT_START,
                    sporsmalstekst = "Fra hvilken dato trengte du ikke lenger sykmeldingen?",
                    svartype = Svartype.DATO,
                    min = fom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
