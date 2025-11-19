package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse

data class SettOppSoknadOptions(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val medlemskapSporsmalTags: List<MedlemskapSporsmalTag>? = emptyList(),
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
)
