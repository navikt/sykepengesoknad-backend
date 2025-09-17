package no.nav.helse.flex.mockdispatcher

import okhttp3.mockwebserver.MockResponse
import org.springframework.http.MediaType

fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
    createMockResponse().addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
