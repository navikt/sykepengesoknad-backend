package no.nav.helse.flex

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJwtTokenValidation
// Erstatter @EnableRetry fra spring-retry. Merk at @Retryable teller annerledes enn før: spring-retry sin
// maxAttempts = 3 betyr tre kall totalt, mens Spring 7 sin maxRetries = 2 betyr to forsøk i tillegg til det
// første. Begge gir altså tre kall, og maxRetries = 2 er den direkte oversettelsen av det vi hadde før.
@EnableResilientMethods
@EnableScheduling
@EnableKafka
class Application

fun main(args: Array<String>) {
    // Lettuce-spring boot interaksjon. Se https://github.com/lettuce-io/lettuce-core/issues/1767
    System.setProperty("io.lettuce.core.jfr", "false")
    runApplication<Application>(*args)
}
