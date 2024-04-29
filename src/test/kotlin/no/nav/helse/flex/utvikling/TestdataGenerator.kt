package no.nav.helse.flex.utvikling

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev")
class TestdataGenerator {
    @PostConstruct
    fun generate() {
        println("Genererer testdata")
    }
}
