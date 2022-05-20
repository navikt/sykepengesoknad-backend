package no.nav.syfo.domain

import java.time.LocalDate

data class ErUtenforVentetidResponse(
    val erUtenforVentetid: Boolean,
    val oppfolgingsdato: LocalDate?
)
