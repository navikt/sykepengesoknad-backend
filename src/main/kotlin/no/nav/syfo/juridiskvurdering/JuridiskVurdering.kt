package no.nav.syfo.juridiskvurdering

import java.time.LocalDate

data class JuridiskVurdering(
    val fodselsnummer: String,
    val sporing: Map<SporingType, List<String>>,
    val lovverk: String,
    val lovverksversjon: LocalDate,
    val paragraf: String,
    val ledd: Int?,
    val punktum: Int?,
    val bokstav: String?,
    val input: Map<String, Any>,
    val output: Map<String, Any>?,
    val utfall: Utfall,
)
