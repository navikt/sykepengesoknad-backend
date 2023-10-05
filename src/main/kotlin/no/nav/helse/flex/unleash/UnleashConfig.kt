package no.nav.helse.flex.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("default")
class UnleashConfig(
    @Value("\${UNLEASH_SERVER_API_URL}") val apiUrl: String,
    @Value("\${UNLEASH_SERVER_API_TOKEN}") val apiToken: String,
    @Value("\${NAIS_APP_NAME}") val appName: String
) : DisposableBean {

    private val config: UnleashConfig = UnleashConfig.builder()
        .appName(appName)
        .unleashAPI("$apiUrl/api")
        .apiKey(apiToken)
        .synchronousFetchOnInitialisation(true)
        .build()
    private val defaultUnleash = DefaultUnleash(config)

    @Bean
    fun unleash(): Unleash {
        return defaultUnleash
    }

    override fun destroy() {
        // Spring trigger denne ved shutdown. Gjøres for å unngå at unleash fortsetter å gjøre kall ut
        defaultUnleash.shutdown()
    }
}
