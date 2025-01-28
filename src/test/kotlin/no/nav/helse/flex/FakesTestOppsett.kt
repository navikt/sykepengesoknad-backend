package no.nav.helse.flex

import no.nav.helse.flex.repository.SykepengesoknadDAOImpl
import no.nav.helse.flex.testoppsett.startMockWebServere
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.web.servlet.MockMvc

const val IGNORED_KAFKA_BROKERS = "localhost:1"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@EnableMockOAuth2Server
@SpringBootTest(
    classes = [Application::class],
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.data.jdbc.repositories.enabled=false",
        "spring.profiles.active=fakes,fakeunleash",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "KAFKA_BROKERS=$IGNORED_KAFKA_BROKERS",
    ],
)
@ComponentScan(
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [SykepengesoknadDAOImpl::class],
        ),
    ],
)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
abstract class FakesTestOppsett : TestOppsettInterfaces {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var server: MockOAuth2Server

    companion object {
        init {
            startMockWebServere()
        }
    }

    @AfterAll
    fun `Vi resetter databasen`() {
        slettDatabase()
    }

    fun slettDatabase() {
    }

    override fun server(): MockOAuth2Server {
        return server
    }

    override fun mockMvc(): MockMvc {
        return mockMvc
    }
}
