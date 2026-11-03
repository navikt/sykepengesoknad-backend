package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.sporsmal.flereInntektskilderGhostEllerAndreInntekterV2
import java.time.LocalDate

fun Sykepengesoknad.erstattGammelAndreInntektskilderMutering(): Sykepengesoknad =
    if (this.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER) != null) {
        this
            .fjernSporsmal(ANDRE_INNTEKTSKILDER)
            .leggTilSporsmaal(
                flereInntektskilderGhostEllerAndreInntekterV2(
                    fom = this.fom!!,
                    tom = this.tom!!,
                    ghostInntekter = this.ghostInntekter ?: emptyList(),
                ),
            )
    } else {
        this
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
