package no.nav.helse.flex.controller.mapper

import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvarAvgittAv
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.SvarAvgittAv
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.domain.sporsmalBuilder
import no.nav.helse.flex.util.EnumUtil.konverter

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
