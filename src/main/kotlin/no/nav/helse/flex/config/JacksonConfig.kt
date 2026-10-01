package no.nav.helse.flex.config

import no.nav.helse.flex.util.jacksonFeatureConfiguration
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class JacksonConfig {
    // Gir Spring sin JsonMapper samme enum-oppsett som ObjectMapper.objectMapper
    @Bean
    fun flexJsonMapperBuilderCustomizer() =
        JsonMapperBuilderCustomizer { builder: JsonMapper.Builder ->
            builder.jacksonFeatureConfiguration()
        }
}
