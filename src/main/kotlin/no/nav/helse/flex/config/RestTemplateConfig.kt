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
    fun restTemplate(callIdInterceptor: CallIdInterceptor): RestTemplate =
        RestTemplateBuilder()
            .additionalInterceptors(callIdInterceptor)
            .build()

    @Bean(name = ["consumerRestTemplate"])
    fun consumerRestTemplate(interceptor: CallIdInterceptor): RestTemplate =
        RestTemplateBuilder()
            .connectTimeout(Duration.ofSeconds(5L))
            .readTimeout(Duration.ofSeconds(10L))
            .additionalInterceptors(interceptor)
            .build()

    @Bean(name = ["plainTextUtf8RestTemplate"])
    fun plainTextUtf8RestTemplate(interceptor: CallIdInterceptor): RestTemplate =
        RestTemplateBuilder()
            .additionalInterceptors(interceptor)
            .messageConverters(StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build()

    @Bean
    fun plainRestTemplate(restTemplateBuilder: RestTemplateBuilder): RestTemplate =
        restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(5L))
            .readTimeout(Duration.ofSeconds(10L))
            .build()
}
