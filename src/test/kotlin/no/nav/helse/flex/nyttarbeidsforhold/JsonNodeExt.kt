package no.nav.helse.flex.nyttarbeidsforhold

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.soknadsopprettelse.sporsmal.AndreInntektskilderMetadata
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString

fun JsonNode.tilAndreInntektskilderMetadata(): AndreInntektskilderMetadata {
    return objectMapper.readValue(this.serialisertTilString())
}
