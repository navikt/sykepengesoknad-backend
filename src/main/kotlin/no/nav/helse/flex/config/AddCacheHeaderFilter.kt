package no.nav.helse.flex.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders.CACHE_CONTROL
import org.springframework.stereotype.Component

@Component
class AddCacheHeaderFilter : Filter {
    override fun destroy() {}
    override fun init(filterConfig: FilterConfig?) {}

    override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
        val req = request as? HttpServletRequest
        val res = response as? HttpServletResponse
        res?.setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        chain?.doFilter(req, res)
    }
}
