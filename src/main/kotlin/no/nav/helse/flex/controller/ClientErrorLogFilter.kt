import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.helse.flex.logger
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class ClientErrorLogFilter : OncePerRequestFilter() {
    private val log = logger()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestWrapper = ContentCachingRequestWrapper(request)
        val responseWrapper = ContentCachingResponseWrapper(response)

        filterChain.doFilter(requestWrapper, responseWrapper)
        val is4xx = responseWrapper.status.toString().startsWith("4")

        if (is4xx) {
            log.warn("HTTP 4xx-feilrespons: ${responseWrapper.status} - ${requestWrapper.requestURI}")
        }
    }
}
