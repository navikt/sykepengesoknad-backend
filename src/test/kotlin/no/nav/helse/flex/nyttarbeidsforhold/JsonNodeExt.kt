package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.soknadsopprettelse.sporsmal.AndreInntektskilderMetadata
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.readValue

fun JsonNode.tilAndreInntektskilderMetadata(): AndreInntektskilderMetadata = objectMapper.readValue(this.serialisertTilString())
