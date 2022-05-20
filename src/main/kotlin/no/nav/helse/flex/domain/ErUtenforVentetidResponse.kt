package no.nav.helse.flex.domain

import java.time.LocalDate

data class ErUtenforVentetidResponse(
    val erUtenforVentetid: Boolean,
    val oppfolgingsdato: LocalDate?
)
