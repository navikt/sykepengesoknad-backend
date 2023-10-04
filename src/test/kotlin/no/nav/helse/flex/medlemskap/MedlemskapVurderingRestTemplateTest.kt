package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.config.AadRestTemplateConfiguration
import no.nav.helse.flex.config.MEDLEMSKAP_VURDERING_REST_TEMPLATE_CONNECT_TIMEOUT
import no.nav.helse.flex.config.MEDLEMSKAP_VURDERING_REST_TEMPLATE_READ_TIMEOUT
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit

/**
 * Tester at konfigurasjonen av RestTemplate brukt til å hente spørsmål om medlemskap
 * fungerer som forventet siden connectTimeout og readTimeout er eksplisitt satt.
 *
 * @see AadRestTemplateConfiguration
 */
@AutoConfigureWebClient
@EnableMockOAuth2Server
@SpringBootTest(
    classes = [
        AadRestTemplateConfiguration::class,
        no.nav.security.token.support.client.spring.oauth2.OAuth2ClientConfiguration::class,
        no.nav.security.token.support.spring.SpringTokenValidationContextHolder::class
    ]
)
class MedlemskapVurderingRestTemplateTest {

    @Autowired
    private lateinit var medlemskapVurderingRestTemplate: RestTemplate

    @Test
    fun failOnConnectTimeout() {
        await().atMost(MEDLEMSKAP_VURDERING_REST_TEMPLATE_CONNECT_TIMEOUT + 1, TimeUnit.SECONDS).untilAsserted {
            assertThrows<ResourceAccessException> {
                medlemskapVurderingRestTemplate.getForEntity(
                    // Non-routable IP addresse. så vi får ikke opprettet en connection.
                    "http://172.0.0.1",
                    String::class.java
                )
            }
        }
    }

    @Test
    @Disabled("Testen tar lang tid å kjøre, og er ikke så viktig å ha med i CI.")
    fun failOnReadTimeout() {
        val responsDelayInSeconds = MEDLEMSKAP_VURDERING_REST_TEMPLATE_READ_TIMEOUT + 1L
        assertThrows<ResourceAccessException> {
            // Oppretter connection, men bruker for lang tid på å svare.
            medlemskapVurderingRestTemplate.getForEntity(
                "https://mockbin.org/delay/${responsDelayInSeconds * 1000}",
                String::class.java
            )
        }
    }
}
