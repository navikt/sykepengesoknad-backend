package no.nav.helse.flex.domain.mapper

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.objectMapper
import java.time.LocalDate

fun String?.parseEgenmeldingsdagerFraSykmelding(): List<LocalDate>? =
    this
        ?.let { objectMapper.readValue(it) as List<String> }
        ?.map { LocalDate.parse(it) }
