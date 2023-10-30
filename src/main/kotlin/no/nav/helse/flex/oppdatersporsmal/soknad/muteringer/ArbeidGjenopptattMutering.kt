package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.FERIE_PERMISJON_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_GRADERT
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_NAR
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.gammeltFormatFeriePermisjonUtlandsoppholdSporsmal
import no.nav.helse.flex.soknadsopprettelse.harFeriePermisjonEllerUtenlandsoppholdSporsmal
import no.nav.helse.flex.soknadsopprettelse.jobbetDuIPeriodenSporsmal
import no.nav.helse.flex.soknadsopprettelse.jobbetDuIPeriodenSporsmalSelvstendigFrilanser
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ferieSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.permisjonSporsmal
import no.nav.helse.flex.soknadsopprettelse.utenlandsoppholdSporsmal
import no.nav.helse.flex.soknadsopprettelse.utlandsSporsmalSelvstendig

fun Sykepengesoknad.arbeidGjenopptattMutering(): Sykepengesoknad {
    if (erIkkeAvType(SELVSTENDIGE_OG_FRILANSERE, ARBEIDSTAKERE, GRADERT_REISETILSKUDD)) {
        return this
    }
    if (soknadstype == GRADERT_REISETILSKUDD) {
        if (!listOf(ARBEIDSTAKER, NAERINGSDRIVENDE, FRILANSER).contains(arbeidssituasjon)) {
            return this
        }
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
                    .filterNot { (_, tag) -> tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT) }
                    .filterNot { (_, tag) -> tag == FERIE_PERMISJON_UTLAND }
                    .filterNot { (_, tag) -> tag == FERIE_V2 }
                    .filterNot { (_, tag) -> tag == PERMISJON_V2 }
                    .filterNot { (_, tag) -> tag == UTLAND_V2 }
                    .filterNot { (_, tag) -> tag == UTLAND }
                    .filterNot { (_, tag) -> tag == UTDANNING }
                    .toMutableList()
            )
        }
    }

    val oppdatertTom = if (arbeidGjenopptattDato == null) {
        this.tom!!
    } else {
        arbeidGjenopptattDato.minusDays(1)
    }

    val oppdaterteSporsmal = if (arbeidssituasjon == ARBEIDSTAKER) {
        jobbetDuIPeriodenSporsmal(
            this.skapOppdaterteSoknadsperioder(
                arbeidGjenopptattDato
            ),
            this.arbeidsgiverNavn!!
        ).toMutableList()
    } else {
        jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
            this.skapOppdaterteSoknadsperioder(
                arbeidGjenopptattDato
            ),
            this.arbeidssituasjon!!
        ).toMutableList()
    }

    if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) {
        oppdaterteSporsmal.add(
            gammeltFormatFeriePermisjonUtlandsoppholdSporsmal(
                this.fom!!,
                oppdatertTom
            )
        )
    } else {
        if (this.arbeidssituasjon == ARBEIDSTAKER) {
            oppdaterteSporsmal.add(ferieSporsmal(this.fom!!, oppdatertTom))
            oppdaterteSporsmal.add(permisjonSporsmal(this.fom, oppdatertTom))
            oppdaterteSporsmal.add(utenlandsoppholdSporsmal(this.fom, oppdatertTom))
        }
        if (this.arbeidssituasjon == NAERINGSDRIVENDE || this.arbeidssituasjon == FRILANSER) {
            oppdaterteSporsmal.add(utlandsSporsmalSelvstendig(this.fom!!, oppdatertTom))
        }
    }

    return this.leggTilSporsmaal(oppdaterteSporsmal)
        .let { oppdatertSoknad ->
            // periode spørsmål som ikke er med i oppdaterteSporsmal fjernes
            oppdatertSoknad.copy(
                sporsmal = oppdatertSoknad.sporsmal
                    .filterNot { spm -> spm.tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT) && oppdaterteSporsmal.none { it.tag == spm.tag } }
                    .filterNot { spm -> spm.tag.startsWith(JOBBET_DU_GRADERT) && oppdaterteSporsmal.none { it.tag == spm.tag } }
            )
        }
}
