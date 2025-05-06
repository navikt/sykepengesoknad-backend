package no.nav.helse.flex.config

import no.nav.syfo.kafka.NAV_CALLID
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.*

private const val REGISTER_CALL_ID = "Nav-Call-Id"

@Component
@Order
class CallIdInterceptor : ClientHttpRequestInterceptor {
    @Throws(IOException::class)
    override fun intercept(
        httpRequest: HttpRequest,
        bytes: ByteArray,
        clientHttpRequestExecution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        Optional
            .ofNullable(MDC.get(NAV_CALLID))
            .ifPresent { callid ->
                httpRequest.headers.add(NAV_CALLID, callid)
                httpRequest.headers.add(REGISTER_CALL_ID, callid)
            }

        return clientHttpRequestExecution.execute(httpRequest, bytes)
    }
}
