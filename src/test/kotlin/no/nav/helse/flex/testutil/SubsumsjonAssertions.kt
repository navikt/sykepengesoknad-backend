package no.nav.helse.flex.testutil

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import java.net.URI

internal object SubsumsjonAssertions {
    private val objectMapper = jacksonObjectMapper()
    private val schema: Schema by lazy {
        val schemaUri = URI("https://raw.githubusercontent.com/navikt/helse/main/subsumsjon/json-schema-1.0.0.json")
        val schemaData = schemaUri.toURL().readText()
        val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
        registry.getSchema(schemaData, InputFormat.JSON)
    }

    internal fun assertSubsumsjonsmelding(melding: JsonNode) {
        val errors: List<Error> = schema.validate(melding.toString(), InputFormat.JSON)
        assertEquals(0, errors.size)
    }

    internal fun assertSubsumsjonsmelding(melding: String) = assertSubsumsjonsmelding(objectMapper.readTree(melding))
}
