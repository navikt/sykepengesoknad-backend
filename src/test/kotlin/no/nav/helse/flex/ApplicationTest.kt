package no.nav.helse.flex

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.config.KafkaListenerEndpointRegistry

class ApplicationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Test
    fun `context laster`() {
    }

    @Test
    fun `Ingen nærmeste leder-topics har registrerte listeners`() {
        listenerRegistry.listenerContainers
            .flatMap {
                it.containerProperties.topics
                    ?.toList()
                    .orEmpty()
            }.filter { it.contains("narmesteleder") }
            .shouldBeEmpty()
    }

    @Test
    fun `Sykmeldingsmottak og søknadsaktivering har fortsatt kjørende listeners`() {
        listOf("sykmelding-sendt-bekreftet", "sykepengesoknad-aktivering").forEach { id ->
            listenerRegistry.getListenerContainer(id).shouldNotBeNull().isRunning shouldBe true
        }
    }
}
