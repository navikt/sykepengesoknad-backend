package no.nav.helse.flex.domain

import no.nav.helse.flex.controller.domain.sykmelding.Tilleggsopplysninger
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import java.time.LocalDate

data class ErUtenforVentetidRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingKafkaMessage? = null,
)

data class VentetidRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingKafkaMessage? = null,
    val returnerPerioderInnenforVentetid: Boolean = false,
)

data class VentetidResponse(
    val ventetid: FomTomPeriode?,
)

data class FomTomPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)
