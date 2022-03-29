package no.nav.syfo.selftest

import org.springframework.stereotype.Component

@Component
class ApplicationState {
    private var isAlive = true

    fun isAlive(): Boolean = isAlive

    fun iAmDead() {
        isAlive = false
    }
}
