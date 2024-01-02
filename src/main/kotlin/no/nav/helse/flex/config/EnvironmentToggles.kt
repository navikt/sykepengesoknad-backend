package no.nav.helse.flex.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @Value("\${fasit.environment.name:p}") private val fasitEnvironmentName: String,
    @Value("\${SKRIVEMODUS}") private val skrivemodus: String,
) {
    fun isProduction() = "p" == fasitEnvironmentName

    fun isNotProduction() = "p" != fasitEnvironmentName

    fun isQ() = "q1" == fasitEnvironmentName

    fun isProductionOrQ() = isProduction() || isQ()

    fun isReadOnly() = "READONLY" == skrivemodus
}
