package no.nav.helse.flex.unleash

import io.getunleash.FakeUnleash
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("fakeunleash")
class UnleashTestConfig() {
    private val fakeUnleash = FakeUnleash()

    @Bean
    fun unleash(): FakeUnleash {
        return fakeUnleash
    }
}
