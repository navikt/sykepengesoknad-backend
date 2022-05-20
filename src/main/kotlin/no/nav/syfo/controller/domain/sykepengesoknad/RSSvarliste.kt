package no.nav.syfo.controller.domain.sykepengesoknad

data class RSSvarliste(
    val sporsmalId: String,
    val svar: List<RSSvar>
)
