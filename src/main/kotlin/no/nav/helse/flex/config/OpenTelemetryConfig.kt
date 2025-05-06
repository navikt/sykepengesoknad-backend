package no.nav.helse.flex.config

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenTelemetryConfig {
    @Bean
    fun tracer(): Tracer = GlobalOpenTelemetry.getTracer("sykepengesoknad-backend")
}
