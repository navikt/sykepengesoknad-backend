package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.sporsmal.flereInntektskilderGhostEllerAndreInntekterV2

fun Sykepengesoknad.muterErstattGammelAndreInntektskilder(): Sykepengesoknad =
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
