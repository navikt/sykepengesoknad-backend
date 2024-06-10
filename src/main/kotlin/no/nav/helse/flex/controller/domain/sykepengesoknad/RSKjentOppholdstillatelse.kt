package no.nav.helse.flex.controller.domain.sykepengesoknad

import java.time.LocalDate

data class RSKjentOppholdstillatelse(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)
