package no.nav.syfo.controller.domain.soknadarbeidsgiver

import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad

data class RSSoknadrelasjon(
    val fnr: String?,
    val orgnummer: String?,
    val navn: String?,
    val soknader: List<RSSykepengesoknad>
)
