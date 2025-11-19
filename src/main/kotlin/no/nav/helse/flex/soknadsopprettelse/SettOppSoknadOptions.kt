package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg

data class SettOppSoknadOptions(
    val sykepengesoknad: Sykepengesoknad,
    val erForsteSoknadISykeforlop: Boolean,
    val harTidligereUtenlandskSpm: Boolean,
    val medlemskapSporsmalTags: List<MedlemskapSporsmalTag>? = emptyList(),
    val kjentOppholdstillatelse: KjentOppholdstillatelse? = null,
    val arbeidsforholdoversiktResponse: List<ArbeidsforholdFraAAreg>? = null,
)
