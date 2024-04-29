package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.GenericContainer

private class RedisContainer : GenericContainer<RedisContainer>("bitnami/redis:6.2")

fun startRedisContainer() {
    RedisContainer().apply {
        withEnv("ALLOW_EMPTY_PASSWORD", "yes")
        withExposedPorts(6379)
        start()

        System.setProperty("REDIS_URI_SESSIONS", "rediss://$host:$firstMappedPort")
        System.setProperty("REDIS_USERNAME_SESSIONS", "default")
        System.setProperty("REDIS_PASSWORD_SESSIONS", "")
    }
}
