package no.nav.helse.flex.domain

import no.nav.helse.flex.controller.domain.sykmelding.Tilleggsopplysninger
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import java.time.LocalDate

data class VenteperiodeRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingKafkaMessage? = null,
    val harForsikring: Boolean = false,
)

data class VenteperiodeResponse(
    val venteperiode: Venteperiode?,
)

data class Venteperiode(
    val fom: LocalDate,
    val tom: LocalDate,
)
