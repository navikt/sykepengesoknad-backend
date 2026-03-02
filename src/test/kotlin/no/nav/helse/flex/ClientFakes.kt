package no.nav.helse.flex

import no.nav.helse.flex.fakes.FlexSykmeldingerClientFake
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ClientFakes {
    @Bean
    fun flexSykmeldingerClient(): FlexSykmeldingerClientFake = FlexSykmeldingerClientFake()
}
