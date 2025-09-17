package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.GenericContainer

// TODO: Bytt fra latest til en konkret versjon når det er tilgjengelig i dockerhub
private class ValkeyContainer : GenericContainer<ValkeyContainer>("bitnamisecure/valkey:latest")

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
