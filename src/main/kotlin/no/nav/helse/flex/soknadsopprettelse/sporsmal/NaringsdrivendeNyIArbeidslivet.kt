package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO
import no.nav.helse.flex.util.DatoUtil.formatterDato
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagSporsmalOmNaringsdrivendeNyIArbeidslivet(
    fom: LocalDate,
    startSykeforlop: LocalDate?,
    sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende?,
): Sporsmal {
    val tidligstDato = finnNaringsdrivendeTidligstDato(startSykeforlop, sykepengegrunnlagNaeringsdrivende)

    return Sporsmal(
        tag = NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
        sporsmalstekst =
            "Har du blitt yrkesaktiv mellom ${formatterDato(tidligstDato)} og frem til du ble sykmeldt " +
                "${formatterDato(fom)}?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO,
                    sporsmalstekst = "Når ble du yrkesaktiv?",
                    svartype = Svartype.DATO,
                    min = tidligstDato.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    max = fom.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                ),
            ),
    )
}
