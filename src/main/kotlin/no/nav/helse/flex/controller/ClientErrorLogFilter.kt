import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ClientErrorLogFilter : OncePerRequestFilter() {
    private val log = logger()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        log.info("Log før doFilter ${request.requestURI}")
        filterChain.doFilter(request, response)
        log.info("Log før doFilter ${response.status}")

        val is4xx = response.status.toString().startsWith("4")
        val is2xx = response.status.toString().startsWith("2")

        if (is2xx) log.info("HTTP 2xx-feilrespons: ${response.status} - ${request.requestURI}")
        if (is4xx) log.warn("HTTP 4xx-feilrespons: ${response.status} - ${request.requestURI}")
    }
}
