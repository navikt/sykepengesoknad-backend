package no.nav.helse.flex.domain

import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.Rolle
import no.nav.helse.flex.sykepengesoknad.kafka.SelvstendigNaringsdrivendeDTO

data class SelvstendigNaringsdrivendeInfo(
    val roller: List<BrregRolle>,
    val sykepengegrunnlagNaeringsdrivende: SykepengegrunnlagNaeringsdrivende? = null,
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
