package no.nav.helse.flex.controller.domain.sykepengesoknad

import com.fasterxml.jackson.databind.JsonNode

data class RSSporsmal(
    val id: String?,
    val tag: String,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val svartype: RSSvartype?,
    val min: String?,
    val max: String?,
    val metadata: JsonNode?,
    val kriterieForVisningAvUndersporsmal: RSVisningskriterie?,
    val svar: List<RSSvar>,
    val undersporsmal: List<RSSporsmal>,
)
