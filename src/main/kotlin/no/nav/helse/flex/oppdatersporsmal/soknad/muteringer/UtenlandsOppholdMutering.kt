package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.FERIE
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR
import no.nav.helse.flex.soknadsopprettelse.FERIE_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.FERIE_PERMISJON_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR
import no.nav.helse.flex.soknadsopprettelse.UTLAND_NAR_V2
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.harFeriePermisjonEllerUtenlandsoppholdSporsmal
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import java.util.*

fun Sykepengesoknad.oppdaterMedSvarPaUtlandsopphold(): Sykepengesoknad {
    if (erIkkeAvType(Soknadstype.ARBEIDSTAKERE)) {
        return this
    }

    val gyldigeFerieperioder = hentGyldigePerioder(
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) FERIE else FERIE_V2,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) FERIE_NAR else FERIE_NAR_V2
    )

    val gyldigeUtlandsperioder = hentGyldigePerioder(
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) UTLAND else UTLAND_V2,
        if (harFeriePermisjonEllerUtenlandsoppholdSporsmal()) UTLAND_NAR else UTLAND_NAR_V2
    )

    val harUtlandsoppholdUtenforHelgOgFerie = gyldigeUtlandsperioder
        .filter { DatoUtil.periodeErUtenforHelg(it) }
        .any { periode -> DatoUtil.periodeHarDagerUtenforAndrePerioder(periode, gyldigeFerieperioder) }

    val maybeSoktOmSykepengerSporsmal = getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)

    if (harUtlandsoppholdUtenforHelgOgFerie) {
        val oppholdUtland = Sporsmal(
            tag = UTLANDSOPPHOLD_SOKT_SYKEPENGER,
            sporsmalstekst = "Har du søkt om å beholde sykepengene for de dagene du var utenfor EØS?",
            svartype = Svartype.JA_NEI,
            pavirkerAndreSporsmal = false,
            svar = maybeSoktOmSykepengerSporsmal?.svar ?: emptyList(),
            undersporsmal = Collections.emptyList()
        )

        return this.leggTilSporsmaal(oppholdUtland)
    }
    return fjernSporsmal(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
}

private fun Sykepengesoknad.hentGyldigePerioder(hva: String, nar: String): List<Periode> {
    return if (this.harFeriePermisjonEllerUtenlandsoppholdSporsmal()) {
        val harFeriePermisjonUtlandsopphold =
            this.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND)?.forsteSvar == "JA"
        if (harFeriePermisjonUtlandsopphold && this.getSporsmalMedTagOrNull(hva)?.forsteSvar == "CHECKED") {
            getGyldigePeriodesvar(this.getSporsmalMedTag(nar))
        } else {
            Collections.emptyList()
        }
    } else {
        val harSvartJa = this.getSporsmalMedTagOrNull(hva)?.forsteSvar == "JA"
        if (harSvartJa) {
            getGyldigePeriodesvar(this.getSporsmalMedTag(nar))
        } else {
            Collections.emptyList()
        }
    }
}

private fun getGyldigePeriodesvar(sporsmal: Sporsmal): List<Periode> {
    return sporsmal.svar.asSequence()
        .map { it.verdi }
        .map { PeriodeMapper.jsonTilOptionalPeriode(it) } // TODO: Kan enders?
        .filter { it.isPresent }
        .map { it.get() }
        .filter { periode -> DatoUtil.periodeErInnenforMinMax(periode, sporsmal.min, sporsmal.max) }
        .toList()
        .toList()
}
