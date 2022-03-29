package no.nav.syfo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @Value("\${fasit.environment.name:p}") private val fasitEnvironmentName: String
) {
    fun isProduction() = "p" == fasitEnvironmentName
    fun isNotProduction() = "p" != fasitEnvironmentName
    fun isQ() = "q1" == fasitEnvironmentName
    fun isProductionOrQ() = isProduction() || isQ()
}
