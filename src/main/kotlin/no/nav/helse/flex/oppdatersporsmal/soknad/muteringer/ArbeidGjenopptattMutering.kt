package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.skapOppdaterteSoknadsperioder
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*

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
                sporsmal =
                    sporsmal
                        .asSequence()
                        .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_GRADERT) }
                        .filterNot { (_, tag) -> tag.startsWith(JOBBET_DU_100_PROSENT) }
                        .filterNot { (_, tag) -> tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT) }
                        .filterNot { (_, tag) -> tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS) }
                        .filterNot { (_, tag) -> tag == FERIE_V2 }
                        .filterNot { (_, tag) -> tag == PERMISJON_V2 }
                        .filterNot { (_, tag) -> tag == UTLAND_V2 }
                        .filterNot { (_, tag) -> tag == UTLAND }
                        .filterNot { (_, tag) -> tag == OPPHOLD_UTENFOR_EOS }
                        .filterNot { (_, tag) -> tag == UTDANNING }
                        .toMutableList(),
            )
        }
    }

    val oppdatertTom =
        if (arbeidGjenopptattDato == null) {
            this.tom!!
        } else {
            arbeidGjenopptattDato.minusDays(1)
        }

    val oppdaterteSporsmal =
        if (arbeidssituasjon == ARBEIDSTAKER) {
            jobbetDuIPeriodenSporsmal(
                this.skapOppdaterteSoknadsperioder(
                    arbeidGjenopptattDato,
                ),
                this.arbeidsgiverNavn!!,
            ).toMutableList()
        } else {
            jobbetDuIPeriodenSporsmalSelvstendigFrilanser(
                this.skapOppdaterteSoknadsperioder(
                    arbeidGjenopptattDato,
                ),
                this.arbeidssituasjon!!,
            ).toMutableList()
        }

    val utlandArbeidstaker =
        if (this.sporsmal.any { it.tag == UTLAND_V2 }) {
            gammeltUtenlandsoppholdArbeidstakerSporsmal(this.fom!!, oppdatertTom)
        } else {
            oppholdUtenforEOSSporsmal(this.fom!!, oppdatertTom)
        }

    val utlandNaringsdrivende =
        if (this.sporsmal.any { it.tag == UTLAND }) {
            gammeltUtenlandsoppholdSelvstendigSporsmal(this.fom, oppdatertTom)
        } else {
            oppholdUtenforEOSSporsmal(this.fom, oppdatertTom)
        }

    val sporsmalSomSkalFjernes = mutableListOf<String>()

    if (this.arbeidssituasjon == ARBEIDSTAKER) {
        oppdaterteSporsmal.add(ferieSporsmal(this.fom, oppdatertTom))
        oppdaterteSporsmal.add(permisjonSporsmal(this.fom, oppdatertTom))
        oppdaterteSporsmal.add(utlandArbeidstaker)
        val arbeidsforholdSporsmal =
            nyttArbeidsforholdSporsmal(
                nyeArbeidsforhold = this.arbeidsforholdFraAareg,
                denneSoknaden = this,
                oppdatertTom = oppdatertTom,
            )
        oppdaterteSporsmal.addAll(
            arbeidsforholdSporsmal,
        )
        if (arbeidsforholdSporsmal.isEmpty()) {
            sporsmalSomSkalFjernes.add(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
        }
    }
    if (this.arbeidssituasjon == NAERINGSDRIVENDE || this.arbeidssituasjon == FRILANSER) {
        oppdaterteSporsmal.add(utlandNaringsdrivende)
    }

    return this
        .leggTilSporsmaal(oppdaterteSporsmal)
        .let { oppdatertSoknad ->
            // periode spørsmål som ikke er med i oppdaterteSporsmal fjernes
            oppdatertSoknad.copy(
                sporsmal =
                    oppdatertSoknad.sporsmal
                        .filterNot { spm ->
                            spm.tag.startsWith(ARBEID_UNDERVEIS_100_PROSENT) && oppdaterteSporsmal.none { it.tag == spm.tag }
                        }.filterNot { spm -> spm.tag.startsWith(JOBBET_DU_GRADERT) && oppdaterteSporsmal.none { it.tag == spm.tag } },
            )
        }.let { it.copy(sporsmal = it.sporsmal.filter { spm -> fjernIndexFraTag(spm.tag) !in sporsmalSomSkalFjernes }) }
}
