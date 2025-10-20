package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET_DATO
import no.nav.helse.flex.util.DatoUtil.formatterDato
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagSporsmalOmNaringsdrivendeVirksomhetenDinAvviklet(fom: LocalDate): Sporsmal =
    Sporsmal(
        tag = NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET,
        sporsmalstekst = "Avviklet du virksomheten din før du ble sykmeldt ${formatterDato(fom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = NARINGSDRIVENDE_VIRKSOMHETEN_DIN_AVVIKLET_DATO,
                    sporsmalstekst = "Når avviklet du virksomheten din?",
                    svartype = Svartype.DATO,
                    min = null,
                    max = fom.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
