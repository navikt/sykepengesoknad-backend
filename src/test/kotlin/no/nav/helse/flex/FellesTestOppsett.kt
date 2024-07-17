package no.nav.helse.flex

import io.getunleash.FakeUnleash
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.client.kvitteringer.SykepengesoknadKvitteringerClient
import no.nav.helse.flex.juridiskvurdering.juridiskVurderingTopic
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.personhendelse.AutomatiskInnsendingVedDodsfall
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.helse.flex.testdata.DatabaseReset
import no.nav.helse.flex.testoppsett.startAlleContainere
import no.nav.helse.flex.testoppsett.startMockWebServere
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@AutoConfigureObservability
abstract class FellesTestOppsett {
    companion object {
        val pdlMockWebserver: MockWebServer
        val medlemskapMockWebServer: MockWebServer
        private val inntektskomponentenMockWebServer: MockWebServer
        private val eregMockWebServer: MockWebServer
        private val yrkesskadeMockWebServer: MockWebServer
        private val innsendingApiMockWebServer: MockWebServer

        init {
            startAlleContainere()
            startMockWebServere().also {
                pdlMockWebserver = it.pdlMockWebserver
                medlemskapMockWebServer = it.medlemskapMockWebServer
                inntektskomponentenMockWebServer = it.inntektskomponentenMockWebServer
                eregMockWebServer = it.eregMockWebServer
                yrkesskadeMockWebServer = it.yrkesskadeMockWebServer
                innsendingApiMockWebServer = it.innsendingApiMockWebServer
            }
        }
    }

    @MockBean
    lateinit var rebehandlingsSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer

    @Autowired
    lateinit var istilgangskontrollRestTemplate: RestTemplate

    @Autowired
    lateinit var flexSyketilfelleRestTemplate: RestTemplate

    @Autowired
    lateinit var fakeUnleash: FakeUnleash

    @Autowired
    lateinit var behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering

    lateinit var istilgangskontrollMockRestServiceServer: MockRestServiceServer
    lateinit var flexSyketilfelleMockRestServiceServer: MockRestServiceServer

    @PostConstruct
    fun setupRestServiceServers() {
        istilgangskontrollMockRestServiceServer =
            MockRestServiceServer.bindTo(istilgangskontrollRestTemplate).ignoreExpectOrder(true).build()
        flexSyketilfelleMockRestServiceServer =
            MockRestServiceServer.bindTo(flexSyketilfelleRestTemplate).ignoreExpectOrder(true).build()
    }

    @MockBean
    lateinit var sykepengesoknadKvitteringerClient: SykepengesoknadKvitteringerClient

    @SpyBean
    lateinit var automatiskInnsendingVedDodsfall: AutomatiskInnsendingVedDodsfall

    @SpyBean
    lateinit var aivenKafkaProducer: AivenKafkaProducer

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var databaseReset: DatabaseReset

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var sykepengesoknadKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var juridiskVurderingKafkaConsumer: Consumer<String, String>

    @Autowired
    @Qualifier("sykmeldingRetryProducer")
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @BeforeAll
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

    @AfterAll
    fun `Disable unleash toggles`() {
        fakeUnleash.disableAll()
    }

    @BeforeAll
    fun `Vi leser sykepengesoknad kafka topicet og feiler om noe eksisterer`() {
        sykepengesoknadKafkaConsumer.subscribeHvisIkkeSubscribed(SYKEPENGESOKNAD_TOPIC)
        sykepengesoknadKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser juridiskvurdering kafka topicet og feiler om noe eksisterer`() {
        juridiskVurderingKafkaConsumer.subscribeHvisIkkeSubscribed(juridiskVurderingTopic)
        juridiskVurderingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }
}
