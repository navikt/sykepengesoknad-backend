package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_V2

fun Sykepengesoknad.fjernFravaerFoerSykmeldingenV2() =
    if (this.getSporsmalMedTagOrNull(FRAVAR_FOR_SYKMELDINGEN_V2) != null) {
        logger().info("Fjerner sporsmal med tag $FRAVAR_FOR_SYKMELDINGEN_V2 for soknad ${this.id}")
        this.fjernSporsmal(FRAVAR_FOR_SYKMELDINGEN_V2)
    } else {
        this
    }
