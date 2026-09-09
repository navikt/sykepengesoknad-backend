package no.nav.helse.flex.testutil

import no.nav.helse.flex.util.objectMapper
import tools.jackson.module.kotlin.readValue

fun String.jsonTilHashMap(): Map<String, Any> = objectMapper.readValue(this)
