package no.nav.helse.flex.testoppsett

fun startAlleContainere() {
    startRedisContainer()
    startKafkaContainer()
    startPostgresContainer()
}
