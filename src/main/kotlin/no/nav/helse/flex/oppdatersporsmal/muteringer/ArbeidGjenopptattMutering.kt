package no.nav.helse.flex.oppdatersporsmal.muteringer

import no.nav.helse.flex.domain.Soknadstype.ARBEIDSTAKERE
import no.nav.helse.flex.domain.Soknadstype.SELVSTENDIGE_OG_FRILANSERE
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.FERIE_PERMISJON_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.ferieSporsmal
import no.nav.helse.flex.soknadsopprettelse.gammeltFormatFeriePermisjonUtlandsoppholdSporsmal
import no.nav.helse.flex.soknadsopprettelse.harFeriePermisjonEllerUtenlandsoppholdSporsmal
import no.nav.helse.flex.soknadsopprettelse.jobbetDuIPeriodenSporsmal
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.utdanningsSporsmal
import no.nav.helse.flex.soknadsopprettelse.utenlandsoppholdSporsmal

fun Sykepengesoknad.arbeidGjenopptattMutering(): Sykepengesoknad {
    if (erIkkeAvType(SELVSTENDIGE_OG_FRILANSERE, ARBEIDSTAKERE)) {
        return this
    }

    val arbeidGjenopptattDato = this.finnGyldigDatoSvar(TILBAKE_I_ARBEID, TILBAKE_NAR)
    if (arbeidGjenopptattDato != null) {
        if (arbeidGjenopptattDato == this.fom) {
            // Fjerner spørsmål som forsvinner
            return this.copy(
                sporsmal = sporsmal
                    .asSequence()
                    .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_GRADERT) }
                    .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_100_PROSENT) }
                    .filterNot { (_, tag) -> tag == FERIE_PERMISJON_UTLAND }
                    .filterNot { (_, tag) -> tag == FERIE_V2 }
                    .filterNot { (_, tag) -> tag == PERMISJON_V2 }
                    .filterNot { (_, tag) -> tag == UTLAND_V2 }
                    .filterNot { (_, tag) -> tag == UTDANNING }
                    .toMutableList()
            )
        }
    }

    val oppdatertTom = if (arbeidGjenopptattDato == null) {
        this.tom
    } else {
        arbeidGjenopptattDato.minusDays(1)
    }

    val oppdaterteSporsmal = jobbetDuIPeriodenSporsmal(
        this.skapOppdaterteSoknadsperioder(
            arbeidGjenopptattDato
        ),
        this.arbeidsgiverNavn!!,
    ).toMutableList()

    if (!oppdatertTom!!.isBefore(this.fom)) {
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) {
            oppdaterteSporsmal.add(
                gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(
                    this.fom!!,
                    oppdatertTom
                )
            )
        } else {
            oppdaterteSporsmal.add(ferieSporsmal(this.fom!!, oppdatertTom))
            oppdaterteSporsmal.add(permisjonSporsmal(this.fom, oppdatertTom))
            oppdaterteSporsmal.add(utenlandsoppholdSporsmal(this.fom, oppdatertTom))
        }
        oppdaterteSporsmal.add(
            utdanningsSporsmal(
                this.fom,
                oppdatertTom
            )
        )
    }

    return this.leggTilSporsmaal(oppdaterteSporsmal)
}
