import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.helse.flex.logger
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(1)
class ClientErrorLogFilter : OncePerRequestFilter() {
    private val log = logger()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)

        val is4xx = response.status.toString().startsWith("4")
        val is2xx = response.status.toString().startsWith("2")
        log.info("HTTP ${response.status} - ${request.requestURI}")
        if (is2xx) log.info("HTTP 2xx-feilrespons: ${response.status} - ${request.requestURI}")
        if (is4xx) log.warn("HTTP 4xx-feilrespons: ${response.status} - ${request.requestURI}")
    }
}
