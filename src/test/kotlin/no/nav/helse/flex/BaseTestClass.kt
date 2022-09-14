package no.nav.helse.flex

import no.nav.helse.flex.client.bucketuploader.BucketUploaderClient
import no.nav.helse.flex.juridiskvurdering.juridiskVurderingTopic
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.kafka.sykepengesoknadTopic
import no.nav.helse.flex.service.AutomatiskInnsendingService
import no.nav.helse.flex.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.helse.flex.testdata.DatabaseReset
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
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
import kotlin.concurrent.thread

private class RedisContainer : GenericContainer<RedisContainer>("bitnami/redis:6.2")
private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@AutoConfigureMetrics
abstract class BaseTestClass {

    companion object {
        val pdlMockWebserver: MockWebServer

        init {
            val threads = mutableListOf<Thread>()

            thread {
                RedisContainer().apply {
                    withExposedPorts(6379)
                    val passord = "hemmelig"
                    withEnv("REDIS_PASSWORD", passord)
                    start()
                    System.setProperty("spring.redis.host", host)
                    System.setProperty("spring.redis.port", firstMappedPort.toString())
                    System.setProperty("spring.redis.password", passord)
                }
            }.also { threads.add(it) }

            thread {
                KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1")).apply {
                    start()
                    System.setProperty("KAFKA_BROKERS", bootstrapServers)
                    System.setProperty("on-prem-kafka.bootstrap-servers", bootstrapServers)
                }
            }.also { threads.add(it) }

            thread {
                PostgreSQLContainer14().apply {
                    start()
                    System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                    System.setProperty("spring.datasource.username", username)
                    System.setProperty("spring.datasource.password", password)
                }
            }.also { threads.add(it) }

            pdlMockWebserver = MockWebServer().apply {
                System.setProperty("pdl.api.url", "http://localhost:$port")
                dispatcher = PdlMockDispatcher
            }

            threads.forEach { it.join() }
        }
    }

    @MockBean
    lateinit var bucketUploaderClient: BucketUploaderClient

    @MockBean
    lateinit var rebehandlingsSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer

    @Autowired
    lateinit var syfotilgangskontrollRestTemplate: RestTemplate

    @Autowired
    lateinit var flexSyketilfelleRestTemplate: RestTemplate

    @Autowired
    lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    var syfotilgangskontrollMockRestServiceServer: MockRestServiceServer? = null
    var flexSyketilfelleMockRestServiceServer: MockRestServiceServer? = null

    @PostConstruct
    fun setupRestServiceServers() {
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
