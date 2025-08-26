package no.nav.helse.flex

import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidConsumer
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
import no.nav.helse.flex.repository.SykepengesoknadDAOPostgres
import no.nav.helse.flex.testdata.DatabaseReset
import no.nav.helse.flex.testoppsett.startMockWebServere
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.Producer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestClient

const val IGNORED_KAFKA_BROKERS = "localhost:1"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@EnableMockOAuth2Server
@SpringBootTest(
    classes = [Application::class, MockWebServereConfig::class],
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.data.jdbc.repositories.enabled=false",
        "spring.profiles.active=fakes,fakeunleash,frisktilarbeid",
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
            classes = [SykepengesoknadDAOPostgres::class],
        ),
    ],
)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
abstract class FakesTestOppsett : TestOppsettInterfaces {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var friskTilArbeidConsumer: FriskTilArbeidConsumer

    @Autowired
    lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    lateinit var databaseReset: DatabaseReset

    companion object {
        init {
            startMockWebServere()
        }
    }

    @AfterAll
    @BeforeAll
    fun `Vi resetter databasen`() {
        databaseReset.resetDatabase()
    }

    @AfterAll
    fun `Reset kafka`() {
        SoknadKafkaProducerFake.records.clear()
    }

    override fun server(): MockOAuth2Server = server

    override fun mockMvc(): MockMvc = mockMvc

    override fun kafkaProducer(): Producer<String, String> = kafkaProducer

    @TestConfiguration
    class EnhetsregisterMockConfig {
        @Bean
        fun enhetsregisterMockServer(
            @Autowired restClientBuilder: RestClient.Builder,
        ): MockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    }
}
