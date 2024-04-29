package no.nav.helse.flex.utvikling

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

@Component
@EnableMockOAuth2Server
@Profile("dev")
class AuthenticationHeaderFilter : Filter {
    @Autowired
    lateinit var server: MockOAuth2Server

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse?,
        chain: FilterChain,
    ) {
        println("AuthenticationHeaderFilter")
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse?
        val mutableRequest = MutableHttpServletRequest(httpRequest)
        // TODO mutableRequest.putHeader("Authorization", "Bearer ${server.skapAzureJwt()}")
        chain.doFilter(mutableRequest, httpResponse)
    }
}

internal class MutableHttpServletRequest(private val original: HttpServletRequest) : HttpServletRequestWrapper(
    original,
) {
    private val customHeaders: MutableMap<String, String> = HashMap()

    fun putHeader(
        name: String,
        value: String,
    ) {
        customHeaders[name] = value
    }

    override fun getHeader(name: String): String? {
        val headerValue = customHeaders[name]
        if (headerValue != null) {
            return headerValue
        }
        return original.getHeader(name)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        if (customHeaders.containsKey(name)) {
            return Collections.enumeration(Arrays.asList(customHeaders[name]))
        }
        return original.getHeaders(name)
    }

    override fun getHeaderNames(): Enumeration<String> {
        // Legg til egendefinerte headere til de eksisterende
        val headerNames: MutableSet<String> = HashSet(customHeaders.keys)
        val originalHeaderNames: Enumeration<String> = original.headerNames
        while (originalHeaderNames.hasMoreElements()) {
            headerNames.add(originalHeaderNames.nextElement())
        }
        return Collections.enumeration(headerNames)
    }
}

@Configuration
@Profile("dev")
class FilterConfig {
    @Autowired
    lateinit var authenticationHeaderFilter: AuthenticationHeaderFilter

    @Bean
    fun customHeaderFilter(): FilterRegistrationBean<AuthenticationHeaderFilter> {
        val registrationBean: FilterRegistrationBean<AuthenticationHeaderFilter> =
            FilterRegistrationBean<AuthenticationHeaderFilter>()
        registrationBean.setFilter(authenticationHeaderFilter)
        registrationBean.addUrlPatterns("/*") // Angi hvilke URL-mønstre dette filteret skal gjelde for
        registrationBean.setOrder(Int.MIN_VALUE) // Sett rekkefølgen til så lavt som mulig for å kjøre tidlig

        return registrationBean
    }
}
