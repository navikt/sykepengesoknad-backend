package no.nav.syfo.config

import org.springframework.http.HttpHeaders.CACHE_CONTROL
import org.springframework.stereotype.Component
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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
