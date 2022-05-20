package no.nav.helse.flex.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.OBJECT_MAPPER

fun String.jsonTilHashMap(): Map<String, Any> = OBJECT_MAPPER.readValue(this)
