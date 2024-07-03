package no.nav.helse.flex.utvikling

import no.nav.helse.flex.Application
import no.nav.helse.flex.mockdispatcher.FlexSyketilfelleMockDispatcher
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableMockOAuth2Server
@Profile("dev")
class AuthConfig

fun main(args: Array<String>) {
    // Denne har ikke MockWebServer i testene
    MockWebServer().apply {
        System.setProperty("flex.syketilfelle.url", "http://localhost:$port")
        dispatcher = FlexSyketilfelleMockDispatcher
    }

    System.setProperty("spring.kafka.consumer.auto-offset-reset", "earliest")
    System.setProperty("server.port", "80")
    System.setProperty("spring.profiles.active", "dev,test,sykmeldinger")
    runApplication<Application>(*args)
}
