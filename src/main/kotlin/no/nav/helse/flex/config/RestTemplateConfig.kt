package no.nav.helse.flex.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Duration

@Configuration
class RestTemplateConfig {

    @Bean
    fun restTemplate(callIdInterceptor: CallIdInterceptor): RestTemplate {
        return RestTemplateBuilder()
            .additionalInterceptors(callIdInterceptor)
            .build()
    }

    @Bean(name = ["consumerRestTemplate"])
    fun consumerRestTemplate(interceptor: CallIdInterceptor): RestTemplate {
        return RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5L))
            .setReadTimeout(Duration.ofSeconds(10L))
            .additionalInterceptors(interceptor)
            .build()
    }

    @Bean(name = ["plainTextUtf8RestTemplate"])
    fun plainTextUtf8RestTemplate(interceptor: CallIdInterceptor): RestTemplate {
        return RestTemplateBuilder()
            .additionalInterceptors(interceptor)
            .messageConverters(StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build()
    }

    @Bean
    fun plainRestTemplate(
        restTemplateBuilder: RestTemplateBuilder
    ): RestTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5L))
            .setReadTimeout(Duration.ofSeconds(10L)).build()
}
