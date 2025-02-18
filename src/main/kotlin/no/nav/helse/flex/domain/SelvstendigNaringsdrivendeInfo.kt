package no.nav.helse.flex.domain

data class SelvstendigNaringsdrivendeInfo(
    val roller: List<BrregRolle>,
)

data class BrregRolle(
    val orgnummer: String,
    val orgnavn: String,
    val rolletype: String,
)
