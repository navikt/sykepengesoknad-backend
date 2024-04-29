import no.nav.helse.flex.testoppsett.startKafkaContainer
import no.nav.helse.flex.testoppsett.startPostgresContainer
import no.nav.helse.flex.testoppsett.startRedisContainer
import kotlin.concurrent.thread

fun startAlleContainere() {
    listOf(
        thread {
            startRedisContainer()
        },
        thread {
            startKafkaContainer()
        },
        thread {
            startPostgresContainer()
        },
    ).forEach { it.join() }
}
