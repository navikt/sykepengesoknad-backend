package no.nav.helse.flex

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

class ApplicationHealthTest : BaseTestClass() {

    @Autowired
    private lateinit var applicationAvailability: ApplicationAvailability

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Test
    fun liveness() {
        // App UP
        applicationAvailability.livenessState shouldBeEqualTo LivenessState.CORRECT
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/health/liveness"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"))

        // Tas ned
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            LivenessState.BROKEN
        )

        // App DOWN
        applicationAvailability.livenessState shouldBeEqualTo LivenessState.BROKEN
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/health/liveness"))
            .andExpect(MockMvcResultMatchers.status().isServiceUnavailable)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("DOWN"))

        // Tas opp igjen
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            LivenessState.CORRECT
        )
    }

    @Test
    fun readiness() {
        // App UP
        applicationAvailability.readinessState shouldBeEqualTo ReadinessState.ACCEPTING_TRAFFIC
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/health/readiness"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"))

        // Skal ikke motta mer trafikk
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.REFUSING_TRAFFIC
        )

        // App OUT_OF_SERVICE
        applicationAvailability.readinessState shouldBeEqualTo ReadinessState.REFUSING_TRAFFIC
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/health/readiness"))
            .andExpect(MockMvcResultMatchers.status().isServiceUnavailable)
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("OUT_OF_SERVICE"))

        // Skal motta trafikk
        AvailabilityChangeEvent.publish(
            applicationEventPublisher,
            this,
            ReadinessState.ACCEPTING_TRAFFIC
        )
    }
}
