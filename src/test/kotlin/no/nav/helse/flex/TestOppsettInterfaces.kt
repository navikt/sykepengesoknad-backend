package no.nav.helse.flex

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.test.web.servlet.MockMvc

interface TestOppsettInterfaces {
    fun server(): MockOAuth2Server

    fun mockMvc(): MockMvc
}
