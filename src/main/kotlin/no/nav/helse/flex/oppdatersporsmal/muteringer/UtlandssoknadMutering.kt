package no.nav.helse.flex.oppdatersporsmal.muteringer

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSGIVER
import no.nav.helse.flex.soknadsopprettelse.bekreftSporsmal

fun Sykepengesoknad.utlandssoknadMuteringer(): Sykepengesoknad {
    if (erIkkeAvType(Soknadstype.OPPHOLD_UTLAND)) {
        return this
    }

    val arbeidsgiverSvar = this.getSporsmalMedTag(ARBEIDSGIVER).svar
    val bekreftSporsmal = bekreftSporsmal(arbeidsgiverSvar.size == 1 && "JA" == arbeidsgiverSvar[0].verdi)

    return this.leggTilSporsmaal(bekreftSporsmal)
}
