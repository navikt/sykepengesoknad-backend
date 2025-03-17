package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.GenericContainer

private class ValkeyContainer : GenericContainer<ValkeyContainer>("bitnami/valkey:8.0.2")

fun startValkeyContainer() {
    ValkeyContainer().apply {
        withEnv("ALLOW_EMPTY_PASSWORD", "yes")
        withExposedPorts(6379)
        start()

        System.setProperty("VALKEY_URI_IDENTER", "valkeys://$host:$firstMappedPort")
        System.setProperty("VALKEY_USERNAME_IDENTER", "default")
        System.setProperty("VALKEY_PASSWORD_IDENTER", "")
    }
}
