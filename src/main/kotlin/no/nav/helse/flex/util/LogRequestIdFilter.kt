package no.nav.helse.flex.util

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val X_REQUEST_ID_HEADER = "x-request-id"
private const val X_REQUEST_ID_MDC_MARKER = "x_request_id"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class LogRequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            request.getHeader(X_REQUEST_ID_HEADER)?.let {
                MDC.put(X_REQUEST_ID_MDC_MARKER, it)
                // Legger på x-request-id på response for lettere feilhåndtering i frontend.
                response.setHeader(X_REQUEST_ID_HEADER, it)
            }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(X_REQUEST_ID_HEADER)
        }
    }
}
