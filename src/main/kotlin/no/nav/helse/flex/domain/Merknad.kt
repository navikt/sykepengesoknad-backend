package no.nav.helse.flex.domain

import java.io.Serializable

data class Merknad(
    val type: String,
    val beskrivelse: String? = null
) : Serializable
