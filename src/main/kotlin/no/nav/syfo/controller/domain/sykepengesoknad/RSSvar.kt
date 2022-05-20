package no.nav.syfo.controller.domain.sykepengesoknad

data class RSSvar(
    val id: String? = null,
    val verdi: String,
    val avgittAv: RSSvarAvgittAv? = null
)
