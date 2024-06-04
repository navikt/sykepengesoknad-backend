package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS

fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)?.forsteSvar == "CHECKED"
}
