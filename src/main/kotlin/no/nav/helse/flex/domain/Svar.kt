package no.nav.helse.flex.domain

import java.io.Serializable

data class Svar(
    val id: String?,
    val verdi: String
) : Serializable
