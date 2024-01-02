package no.nav.helse.flex.controller.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import no.nav.syfo.kafka.NAV_CALLID
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CallIdFilter : Filter {
    override fun init(filterConfig: FilterConfig) {}

    override fun doFilter(
        servletRequest: ServletRequest,
        servletResponse: ServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val navCallid =
                Optional.of(servletRequest)
                    .filter { obj: ServletRequest? -> HttpServletRequest::class.java.isInstance(obj) }
                    .map { obj: ServletRequest? -> HttpServletRequest::class.java.cast(obj) }
                    .filter { request: HttpServletRequest -> !request.requestURI.contains("/internal/") }
                    .map { request: HttpServletRequest ->
                        Optional.ofNullable(request.getHeader(NAV_CALLID))
                            .orElseGet { null }
                    }
                    .orElseGet { UUID.randomUUID().toString() }
            MDC.put(NAV_CALLID, navCallid)
            filterChain.doFilter(servletRequest, servletResponse)
        } finally {
            MDC.remove(NAV_CALLID)
        }
    }

    override fun destroy() {}
}
