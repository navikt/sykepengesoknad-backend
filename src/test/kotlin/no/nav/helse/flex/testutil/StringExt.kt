package no.nav.helse.flex.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.objectMapper

fun String.jsonTilHashMap(): Map<String, Any> = objectMapper.readValue(this)
