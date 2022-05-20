package no.nav.helse.flex

import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ApplicationHealth {

    val log = logger()

    @EventListener
    fun onLivenessEvent(event: AvailabilityChangeEvent<LivenessState>) {
        log.info("LivenessState ${event.state} source ${event.source.javaClass.name}")
    }

    @EventListener
    fun onReadinessEvent(event: AvailabilityChangeEvent<ReadinessState>) {
        log.info("ReadinessState ${event.state} source ${event.source.javaClass.name}")
    }
}
