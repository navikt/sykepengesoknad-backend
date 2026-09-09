package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.util.objectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

fun String?.parseEgenmeldingsdagerFraSykmelding(): List<LocalDate>? =
    this
        ?.let { objectMapper.readValue(it) as List<String> }
        ?.map { LocalDate.parse(it) }
