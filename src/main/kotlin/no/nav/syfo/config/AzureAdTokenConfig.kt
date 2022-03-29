package no.nav.syfo.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class AzureAdTokenConfig {
    @Bean
    fun restTemplateMedProxy(): RestTemplate {
        return RestTemplateBuilder()
            .additionalCustomizers(NaisProxyCustomizer())
            .build()
    }
}
