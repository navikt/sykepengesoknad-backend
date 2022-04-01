package no.nav.syfo

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.syfo.client.bucketuploader.BucketUploaderClient
import no.nav.syfo.client.sykmelding.SyfoSmRegisterClient
import no.nav.syfo.juridiskvurdering.juridiskVurderingTopic
import no.nav.syfo.kafka.producer.AivenKafkaProducer
import no.nav.syfo.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.syfo.kafka.sykepengesoknadTopic
import no.nav.syfo.service.AutomatiskInnsendingService
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.testdata.DatabaseReset
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.annotation.PostConstruct

private class RedisContainer : GenericContainer<RedisContainer>("redis:5.0.3-alpine")
private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@AutoConfigureMetrics
abstract class BaseTestClass {

    companion object {
        val pdlMockWebserver = MockWebServer().apply {
            System.setProperty("pdl.api.url", "http://localhost:$port")
            dispatcher = PdlMockDispatcher
        }

        private val redisContainer = RedisContainer().apply {
            withExposedPorts(6379)
            start()
            System.setProperty("spring.redis.host", host)
            System.setProperty("spring.redis.port", firstMappedPort.toString())
        }

        private val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1")).apply {
            start()
            System.setProperty("KAFKA_BROKERS", bootstrapServers)
        }

        init {
            PostgreSQLContainer14().also {
                it.start()
                System.setProperty("spring.datasource.url", "${it.jdbcUrl}&reWriteBatchedInserts=true")
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    println("Avslutter testcontainers")
                    redisContainer.close()
                    kafkaContainer.close()
                }
            )
        }
    }

    @MockBean
    lateinit var syfoSmRegisterClient: SyfoSmRegisterClient

    @MockBean
    lateinit var bucketUploaderClient: BucketUploaderClient

    @MockBean
    lateinit var rebehandlingsSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer

    @Autowired
    lateinit var narmestelederRestTemplate: RestTemplate

    @Autowired
    lateinit var syfotilgangskontrollRestTemplate: RestTemplate

    @Autowired
    lateinit var flexSyketilfelleRestTemplate: RestTemplate

    @Autowired
    lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    var narmestelederMockRestServiceServer: MockRestServiceServer? = null
    var syfotilgangskontrollMockRestServiceServer: MockRestServiceServer? = null
    var flexSyketilfelleMockRestServiceServer: MockRestServiceServer? = null

    @PostConstruct
    fun setupRestServiceServers() {
        if (narmestelederMockRestServiceServer == null) {
            narmestelederMockRestServiceServer = MockRestServiceServer.createServer(narmestelederRestTemplate)
        }
        if (syfotilgangskontrollMockRestServiceServer == null) {
            syfotilgangskontrollMockRestServiceServer =
                MockRestServiceServer.createServer(syfotilgangskontrollRestTemplate)
        }
        if (flexSyketilfelleMockRestServiceServer == null) {
            flexSyketilfelleMockRestServiceServer = MockRestServiceServer.createServer(flexSyketilfelleRestTemplate)
        }
    }

    @Autowired
    private lateinit var cacheManager: CacheManager

    @SpyBean
    lateinit var automatiskInnsendingService: AutomatiskInnsendingService

    @SpyBean
    lateinit var aivenKafkaProducer: AivenKafkaProducer

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var databaseReset: DatabaseReset

    @Autowired
    lateinit var server: MockOAuth2Server

    fun evictAllCaches() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
    }

    @Autowired
    lateinit var sykepengesoknadKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var juridiskVurderingKafkaConsumer: Consumer<String, String>

    @AfterAll
    fun `Vi leser sykepengesoknad topicet og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        sykepengesoknadKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi leser juridisk vurdering topicet og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi resetter databasen`() {
        databaseReset.resetDatabase()
    }

    @BeforeAll
    fun `Vi leser sykepengesoknad kafka topicet og feiler om noe eksisterer`() {
        sykepengesoknadKafkaConsumer.subscribeHvisIkkeSubscribed(sykepengesoknadTopic)
        sykepengesoknadKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser juridiskvurdering kafka topicet og feiler om noe eksisterer`() {
        juridiskVurderingKafkaConsumer.subscribeHvisIkkeSubscribed(juridiskVurderingTopic)
        juridiskVurderingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }
}
