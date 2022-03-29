package no.nav.syfo.controller.domain.sykepengesoknad

data class RSSporsmal(
    val id: String?,
    val tag: String,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val svartype: RSSvartype?,
    val min: String?,
    val max: String?,
    val pavirkerAndreSporsmal: Boolean,
    val kriterieForVisningAvUndersporsmal: RSVisningskriterie?,
    val svar: List<RSSvar>,
    val undersporsmal: List<RSSporsmal>
)
