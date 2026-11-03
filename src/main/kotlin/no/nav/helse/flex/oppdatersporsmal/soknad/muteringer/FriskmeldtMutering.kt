package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Soknadstype.ARBEIDSLEDIG
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.oppdateringhelpers.finnGyldigDatoSvar
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import java.time.LocalDate

fun Sykepengesoknad.friskmeldtMuteringer(): Sykepengesoknad {
    if (erIkkeAvType(ANNET_ARBEIDSFORHOLD, ARBEIDSLEDIG, GRADERT_REISETILSKUDD)) {
        return this
    }
    if (soknadstype == GRADERT_REISETILSKUDD) {
        if (arbeidssituasjon !in listOf(ANNET, Arbeidssituasjon.ARBEIDSLEDIG)) {
            return this
        }
    }

    val friskmeldtDato = this.finnGyldigDatoSvar(FRISKMELDT, FRISKMELDT_START, relevantSvarVerdi = "NEI")
    if (friskmeldtDato != null) {
        if (friskmeldtDato == this.fom) {
            return this.copy(
                sporsmal =
                    sporsmal
                        .asSequence()
                        .filterNot { (_, tag) -> tag == UTDANNING }
                        .filterNot { (_, tag) -> tag == ARBEIDSLEDIG_UTLAND }
                        .filterNot { (_, tag) -> tag == OPPHOLD_UTENFOR_EOS }
                        .filterNot { (_, tag) -> tag == ANDRE_INNTEKTSKILDER }
                        .filterNot { (_, tag) -> tag == FLERE_INNTEKTSKILDER_GRUPPE }
                        .filterNot { (_, tag) -> tag == ANDRE_INNTEKTSKILDER_V2 }
                        .filterNot { (_, tag) -> tag == PERMISJON_V2 }
                        .toMutableList(),
            )
        }
    }

    val oppdatertTom =
        if (friskmeldtDato == null) {
            this.tom
        } else {
            friskmeldtDato.minusDays(1)
        }

    val skalStilleNyEOSSporsmal =
        if (this.sporsmal.any { it.tag == ARBEIDSLEDIG_UTLAND }) {
            gammeltUtenlandsoppholdArbeidsledigAnnetSporsmal(this.fom!!, oppdatertTom!!)
        } else {
            oppholdUtenforEOSSporsmal(this.fom!!, oppdatertTom!!)
        }

    return this
        .leggTilSporsmaal(skalStilleNyEOSSporsmal)
        .muterAndreInntekter(oppdatertTom)
        .let {
            if (arbeidssituasjon == ANNET) {
                it.leggTilSporsmaal(permisjonSporsmal(this.fom, oppdatertTom))
            } else {
                it
            }
        }
}

fun Sykepengesoknad.muterAndreInntekter(oppdatertTom: LocalDate): Sykepengesoknad =
    this
        .fjernSporsmal(ANDRE_INNTEKTSKILDER)
        .let { soknad ->
            if (!this.ghostInntekter.isNullOrEmpty() && this.sporsmal.any { it.tag == ANDRE_INNTEKTSKILDER_V2 }) {
                soknad.fjernSporsmal(ANDRE_INNTEKTSKILDER_V2)
            } else {
                soknad
            }
        }.leggTilSporsmaal(
            flereInntektskilderGhostEllerAndreInntekterV2(
                fom = this.fom!!,
                tom = oppdatertTom,
                ghostInntekter = this.ghostInntekter ?: emptyList(),
            ),
        )
