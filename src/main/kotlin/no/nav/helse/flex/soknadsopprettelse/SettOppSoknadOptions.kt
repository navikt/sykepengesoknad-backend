package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag

data class SettOppSoknadOptions(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val yrkesskade: YrkesskadeSporsmalGrunnlag,
    val medlemskapSporsmalTags: List<MedlemskapSporsmalTag>? = emptyList(),
    val naringsdrivendeInntektsopplysningerEnabled: Boolean = false,
    val nyttOppholdUtenforEOSEnabled: Boolean = true,
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
)
