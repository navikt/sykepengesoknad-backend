package no.nav.helse.flex.utvikling

import no.nav.helse.flex.Application
import no.nav.helse.flex.mockdispatcher.FlexSyketilfelleMockDispatcher
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import startAlleContainere
import startMockWebServere

@Configuration
@EnableMockOAuth2Server
@Profile("dev")
class AuthConfig

fun main(args: Array<String>) {
    startAlleContainere()
    startMockWebServere()

    // Disse bruker annen konfig i testene. Burde bli fellest
    MockWebServer().apply {
        System.setProperty("flex.syketilfelle.url", "http://localhost:$port")
        dispatcher = FlexSyketilfelleMockDispatcher
    }
// Set earliest kafka
    System.setProperty("spring.kafka.consumer.auto-offset-reset", "earliest")

    System.setProperty("server.port", "80")
    System.setProperty("spring.profiles.active", "dev,test,sykmeldinger")
    runApplication<Application>(*args)
}
