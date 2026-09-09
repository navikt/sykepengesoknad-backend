package no.nav.helse.flex.config

import no.nav.helse.flex.util.medFlexEnumOppsett
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class JacksonConfig {
    /**
     * Gir Spring sin JsonMapper samme enum-oppsett som den delte objectMapper, slik at REST- og
     * Kafka-kontrakten bruker konstantnavnet og ikke toString().
     *
     * Dette settes i kode og ikke med spring.jackson.datatype.enum i application.yaml, fordi
     * src/test/resources/application.yaml skygger hele hovedfila på testklassestien.
     */
    @Bean
    fun flexJsonMapperBuilderCustomizer() =
        JsonMapperBuilderCustomizer { builder: JsonMapper.Builder ->
            builder.medFlexEnumOppsett()
        }
}
