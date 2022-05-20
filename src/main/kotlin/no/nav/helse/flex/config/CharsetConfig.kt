package no.nav.helse.flex.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.charset.StandardCharsets

@Configuration
class CharsetConfig : WebMvcConfigurer {
    override fun configureMessageConverters(converters: List<HttpMessageConverter<*>>) {
        converters
            .filterIsInstance<MappingJackson2HttpMessageConverter>()
            .forEach { converter: HttpMessageConverter<*> -> (converter as MappingJackson2HttpMessageConverter).defaultCharset = StandardCharsets.UTF_8 }
    }
}
