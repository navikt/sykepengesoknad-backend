package no.nav.helse.flex

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableCaching
@EnableJwtTokenValidation
@EnableRetry
@EnableScheduling
@EnableKafka
class Application

fun main(args: Array<String>) {
    // Lettuce-spring boot interaksjon. Se https://github.com/lettuce-io/lettuce-core/issues/1767
    System.setProperty("io.lettuce.core.jfr", "false")
    runApplication<Application>(*args)
}
