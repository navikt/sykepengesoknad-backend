package no.nav.helse.flex.util

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

// Jackson 3 bruker enumens toString() som standard. Applikasjonen bruker konstantnavnet i stedet siden
// flere konstanter har samme toString()-verdi. Se FISKER og JORDBRUKER i Arbeidssituasjon, som begge har verdien
// "selvstendig næringsdrivende".
fun JsonMapper.Builder.jacksonFeatureConfiguration(): JsonMapper.Builder =
    this
        .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
        .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)

val objectMapper: ObjectMapper =
    JsonMapper
        .builder()
        .addModule(kotlinModule())
        .jacksonFeatureConfiguration()
        .build()

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)

fun Any.toJsonNode(): JsonNode = objectMapper.readTree(objectMapper.writeValueAsString(this))
