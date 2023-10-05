package no.nav.helse.flex.unleash

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test")
class UnleashConfig() {

    private val fakeUnleash = FakeUnleash()

    @Bean
    fun unleash(): Unleash {
        return fakeUnleash
    }
}
