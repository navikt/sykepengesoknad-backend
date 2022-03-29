package no.nav.syfo.domain

import java.io.Serializable

data class Svar(
    val id: String?,
    val verdi: String,
    val avgittAv: SvarAvgittAv? = null
) : Serializable
