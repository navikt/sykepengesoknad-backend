package no.nav.syfo.controller.mapper

import no.nav.syfo.controller.domain.sykepengesoknad.*
import no.nav.syfo.domain.*
import no.nav.syfo.util.EnumUtil.konverter

fun RSSvar.mapSvar(): Svar {
    return Svar(
        id = id,
        verdi = verdi,
        avgittAv = avgittAv?.tilSvarAvgittAv()
    )
}

private fun RSSvarAvgittAv.tilSvarAvgittAv(): SvarAvgittAv {
    return when (this) {
        RSSvarAvgittAv.TIDLIGERE_SOKNAD -> SvarAvgittAv.TIDLIGERE_SOKNAD
    }
}

fun RSSporsmal.mapSporsmal(): Sporsmal {
    return Sporsmal(
        id = id,
        tag = tag,
        sporsmalstekst = sporsmalstekst,
        undertekst = undertekst,
        svartype = konverter(Svartype::class.java, this.svartype),
        min = min,
        max = max,
        pavirkerAndreSporsmal = pavirkerAndreSporsmal,
        kriterieForVisningAvUndersporsmal = konverter(
            Visningskriterie::class.java,
            this.kriterieForVisningAvUndersporsmal
        ),
        svar = svar
            .filter { it.verdi.isNotEmpty() }
            .map { it.mapSvar() },
        undersporsmal = undersporsmal.map { it.mapSporsmal() },
    )
}
