package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.BRUKTE_REISETILSKUDDET
import no.nav.helse.flex.soknadsopprettelse.KVITTERINGER
import no.nav.helse.flex.soknadsopprettelse.REISE_MED_BIL
import no.nav.helse.flex.soknadsopprettelse.TRANSPORT_TIL_DAGLIG
import no.nav.helse.flex.soknadsopprettelse.UTBETALING
import no.nav.helse.flex.soknadsopprettelse.reisetilskuddSporsmal

fun Sykepengesoknad.brukteDuReisetilskuddetMutering(): Sykepengesoknad {
    if (erIkkeAvType(Soknadstype.GRADERT_REISETILSKUDD)) {
        return this
    }

    val brukteReisetilskuddet = this.getSporsmalMedTagOrNull(BRUKTE_REISETILSKUDDET)?.forsteSvar == "JA"

    if (brukteReisetilskuddet) {
        val spm = reisetilskuddSporsmal(this.fom!!, this.tom!!, this.arbeidssituasjon!!)
        var soknad = this
        spm.forEach {
            soknad = soknad.leggTilSporsmaal(it)
        }

        return soknad
    }

    return this.copy(
        sporsmal = sporsmal
            .asSequence()
            .filterNot { (_, tag) -> tag == TRANSPORT_TIL_DAGLIG }
            .filterNot { (_, tag) -> tag == REISE_MED_BIL }
            .filterNot { (_, tag) -> tag == KVITTERINGER }
            .filterNot { (_, tag) -> tag == UTBETALING }
            .toMutableList()
    )
}
