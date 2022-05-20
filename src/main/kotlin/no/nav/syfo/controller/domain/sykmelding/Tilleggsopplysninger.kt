package no.nav.syfo.controller.domain.sykmelding

import java.time.LocalDate

data class Tilleggsopplysninger(
    val harForsikring: Boolean?,
    val egenmeldingsperioder: List<Datospenn>?
)

data class Datospenn(val fom: LocalDate, val tom: LocalDate)
