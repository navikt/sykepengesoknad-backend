package no.nav.helse.flex.domain

import no.nav.helse.flex.sykepengesoknad.kafka.Rolle
import no.nav.helse.flex.sykepengesoknad.kafka.SelvstendigNaringsdrivendeDTO

data class SelvstendigNaringsdrivendeInfo(
    val roller: List<BrregRolle>,
) {
    fun tilDto() =
        SelvstendigNaringsdrivendeDTO(
            roller = roller.map { Rolle(it.orgnummer, it.rolletype) },
        )
}

data class BrregRolle(
    val orgnummer: String,
    val orgnavn: String,
    val rolletype: String,
)
