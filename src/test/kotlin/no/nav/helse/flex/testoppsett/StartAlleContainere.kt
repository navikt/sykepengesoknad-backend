package no.nav.helse.flex.testoppsett

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
