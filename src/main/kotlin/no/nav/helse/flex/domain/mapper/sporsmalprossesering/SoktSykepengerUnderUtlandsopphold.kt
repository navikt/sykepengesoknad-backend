package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER

fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean {
    return if (sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)?.forsteSvar !== "CHECKED") {
        false
    } else {
        sykepengesoknad.getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)?.forsteSvar == "JA"
    }
}
