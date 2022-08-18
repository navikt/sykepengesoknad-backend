package no.nav.helse.flex.util

import org.slf4j.MDC
import org.springframework.stereotype.Component
import javax.servlet.FilterChain
import javax.servlet.http.HttpFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private const val X_REQUEST_ID = "x-request-id"

@Component
class LogRequestIdFilter : HttpFilter() {

    override fun doFilter(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            request.getHeader(X_REQUEST_ID)?.let {
                MDC.put(X_REQUEST_ID, it)
                response.setHeader(X_REQUEST_ID, it)
            }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(X_REQUEST_ID)
        }
    }
}
