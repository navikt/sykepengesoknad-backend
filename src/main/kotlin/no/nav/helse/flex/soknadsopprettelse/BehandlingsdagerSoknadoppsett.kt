package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.behandlingsdagerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ferieSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.flereInntektskilderGhostEllerAndreInntekterV2
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSlutt
import java.time.LocalDate

fun Sporsmal.plasseringSporsmalBehandlingsdager(): Int =
    when (this.tag) {
        ANSVARSERKLARING -> -10
        FRAVER_FOR_BEHANDLING -> -9
        FERIE_V2 -> 103
        ARBEID_UTENFOR_NORGE -> 104
        ANDRE_INNTEKTSKILDER -> 105
        ANDRE_INNTEKTSKILDER_V2 -> 105
        FLERE_INNTEKTSKILDER_GRUPPE -> 105
        VAER_KLAR_OVER_AT -> 106
        BEKREFT_OPPLYSNINGER -> 107
        TIL_SLUTT -> 110
        else -> plasseringAvSporsmalSomKanRepeteresFlereGanger()
    }

private fun Sporsmal.plasseringAvSporsmalSomKanRepeteresFlereGanger(): Int =
    if (tag.startsWith(ENKELTSTAENDE_BEHANDLINGSDAGER)) {
        Integer.parseInt(tag.replace(ENKELTSTAENDE_BEHANDLINGSDAGER, ""))
    } else {
        0
    }

fun settOppSykepengesoknadBehandlingsdager(
    fom: LocalDate,
    tom: LocalDate,
    arbeidssituasjon: Arbeidssituasjon,
    soknadPerioder: List<Soknadsperiode>,
    erForsteSoknadISykeforlop: Boolean,
    ghostInntekter: List<KjentInntektskilde>,
): List<Sporsmal> =
    mutableListOf(
        ansvarserklaringSporsmal(),
        flereInntektskilderGhostEllerAndreInntekterV2(
            fom = fom,
            tom = tom,
            ghostInntekter = ghostInntekter,
        ),
    ).also {
        if (erForsteSoknadISykeforlop) {
            it.add(arbeidUtenforNorge())
        }
        if (arbeidssituasjon == ARBEIDSTAKER) {
            it.add(ferieSporsmal(fom, tom))
        }
        it.addAll(behandlingsdagerSporsmal(soknadPerioder, arbeidssituasjon))
        it.add(tilSlutt())
    }.toList()
