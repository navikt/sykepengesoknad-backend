package no.nav.syfo.controller.domain.soknadarbeidsgiver

data class RSSoknadArbeidsgiverRespons(
    val narmesteLedere: List<RSSoknadrelasjon>,
    val humanResources: List<RSSoknadrelasjon>
)
