package no.nav.helse.flex.util

import org.postgresql.util.PGobject
import tools.jackson.module.kotlin.readValue

inline fun <reified T> PGobject.fraPostgresJson(): T {
    val json = this.value ?: error("PGobject mangler verdi")
    return objectMapper.readValue(json)
}
