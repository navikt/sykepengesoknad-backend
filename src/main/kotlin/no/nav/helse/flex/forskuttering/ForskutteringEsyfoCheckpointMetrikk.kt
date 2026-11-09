package no.nav.helse.flex.forskuttering

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@Profile("!fakes")
class ForskutteringEsyfoCheckpointMetrikk(
    private val jdbcTemplate: JdbcTemplate,
    private val meterRegistry: MeterRegistry,
) {
    private val registrertePartisjoner = ConcurrentHashMap.newKeySet<Int>()

    @EventListener(ApplicationReadyEvent::class)
    fun registrerEksisterende() {
        jdbcTemplate
            .query(
                "SELECT partition FROM forskuttering_esyfo_checkpoint WHERE topic = ?",
                { rs, _ -> rs.getInt(1) },
                NARMESTELEDER_ESYFO_LEESAH_TOPIC,
            ).forEach(::registrer)
    }

    fun registrer(partisjon: Int) {
        if (registrertePartisjoner.add(partisjon)) {
            Gauge
                .builder("forskuttering_esyfo_checkpoint_offset", jdbcTemplate) { jdbc ->
                    jdbc
                        .query(
                            "SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint WHERE topic = ? AND partition = ?",
                            { rs, _ -> rs.getLong(1) },
                            NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                            partisjon,
                        ).firstOrNull()
                        ?.toDouble() ?: Double.NaN
                }.tag("topic", NARMESTELEDER_ESYFO_LEESAH_TOPIC)
                .tag("partition", partisjon.toString())
                .register(meterRegistry)
        }
    }
}
