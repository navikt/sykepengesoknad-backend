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
    return sporsmalBuilder()
        .id(this.id)
        .tag(this.tag)
        .sporsmalstekst(this.sporsmalstekst)
        .undertekst(this.undertekst)
        .svartype(konverter(Svartype::class.java, this.svartype))
        .min(this.min)
        .max(this.max)
        .pavirkerAndreSporsmal(this.pavirkerAndreSporsmal)
        .kriterieForVisningAvUndersporsmal(
            konverter(
                Visningskriterie::class.java,
                this.kriterieForVisningAvUndersporsmal
            )
        )
        .svar(
            this.svar
                .filter { it.verdi.isNotEmpty() }
                .map { it.mapSvar() }
        )
        .undersporsmal(this.undersporsmal.map { it.mapSporsmal() })
        .build()
}
