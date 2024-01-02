package no.nav.helse.flex.controller.domain.sykepengesoknad

import java.time.LocalDate

data class RSSoknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val sykmeldingstype: RSSykmeldingstype?,
)
