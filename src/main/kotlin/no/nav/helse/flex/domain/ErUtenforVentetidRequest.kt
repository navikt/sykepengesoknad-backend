package no.nav.helse.flex.domain

import no.nav.helse.flex.controller.domain.sykmelding.Tilleggsopplysninger
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage

data class ErUtenforVentetidRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingKafkaMessage? = null,
)
