package no.nav.syfo.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.util.OBJECT_MAPPER

fun String.jsonTilHashMap(): Map<String, Any> = OBJECT_MAPPER.readValue(this)
