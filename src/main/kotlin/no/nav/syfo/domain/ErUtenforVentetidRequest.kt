package no.nav.syfo.domain

import no.nav.syfo.controller.domain.sykmelding.Tilleggsopplysninger
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage

data class ErUtenforVentetidRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingKafkaMessage? = null
)
