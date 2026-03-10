package no.nav.helse.flex

import no.nav.helse.flex.fakes.FlexSykmeldingerBackendClientFake
import org.springframework.context.annotation.Bean

class ClientFakes {
    @Bean
    fun flexSykmeldingerClient(): FlexSykmeldingerBackendClientFake = FlexSykmeldingerBackendClientFake()
}
