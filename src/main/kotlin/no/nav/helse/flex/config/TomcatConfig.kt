package no.nav.helse.flex.config

import org.springframework.boot.tomcat.TomcatConnectorCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TomcatConfig {
    @Bean
    fun maxLocaleCountCustomizer() =
        TomcatConnectorCustomizer { connector ->
            connector.maxLocaleCount = -1
        }
}
