package no.nav.helse.flex.utvikling

import no.nav.helse.flex.Application
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
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

    System.setProperty("server.port", "80")
    System.setProperty("spring.profiles.active", "dev,test")
    runApplication<Application>(*args)
}
