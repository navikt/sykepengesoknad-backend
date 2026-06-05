package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.sporsmal.lagSporsmalOmNaringsdrivendeVirksomhetenAvviklet

fun Sykepengesoknad.naringsdrivendeErstattGamleSporsmalMutering() =
    if (getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET) != null) {
        logger().warn("Erstatter gamle næringsdrivende spørsmål med nye spørsmål ${this.id}")
        this
            .fjernSporsmal(INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET)
            .leggTilSporsmaal(lagSporsmalOmNaringsdrivendeVirksomhetenAvviklet(fom!!))
    } else {
        this
    }
