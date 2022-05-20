package no.nav.helse.flex.controller.domain.sykepengesoknad

data class RSSvarliste(
    val sporsmalId: String,
    val svar: List<RSSvar>
)
