package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype.ANNET_ARBEIDSFORHOLD
import no.nav.helse.flex.domain.Soknadstype.ARBEIDSLEDIG
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utenlandsoppholdArbeidsledigAnnetSporsmal

fun Sykepengesoknad.friskmeldtMuteringer(): Sykepengesoknad {
    if (erIkkeAvType(ANNET_ARBEIDSFORHOLD, ARBEIDSLEDIG)) {
        return this
    }

    val friskmeldtDato = this.finnGyldigDatoSvar(FRISKMELDT, FRISKMELDT_START, relevantSvarVerdi = "NEI")
    if (friskmeldtDato != null) {
        if (friskmeldtDato == this.fom) {
            // Fjerner spørsmål som forsvinner
            return this.copy(
                sporsmal = sporsmal
                    .asSequence()
                    .filterNot { (_, tag) -> tag == UTDANNING }
                    .filterNot { (_, tag) -> tag == ARBEIDSLEDIG_UTLAND }
                    .filterNot { (_, tag) -> tag == ANDRE_INNTEKTSKILDER }
                    .filterNot { (_, tag) -> tag == PERMISJON_V2 }
                    .toMutableList()
            )
        }
    }

    val oppdatertTom = if (friskmeldtDato == null) {
        this.tom
    } else {
        friskmeldtDato.minusDays(1)
    }

    return this
        .leggTilSporsmaal(utenlandsoppholdArbeidsledigAnnetSporsmal(this.fom!!, oppdatertTom!!))
        .leggTilSporsmaal(andreInntektskilderArbeidsledig(this.fom, oppdatertTom))
        .run {
            if (this.arbeidssituasjon == Arbeidssituasjon.ANNET) {
                return@run this.leggTilSporsmaal(permisjonSporsmal(this.fom!!, oppdatertTom))
            }
            return@run this
        }
}
