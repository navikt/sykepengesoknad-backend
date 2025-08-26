package no.nav.helse.flex

import io.getunleash.FakeUnleash
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.client.bregDirect.DagmammaStatus
import no.nav.helse.flex.client.bregDirect.EnhetsregisterClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.kvitteringer.SykepengesoknadKvitteringerClient
import no.nav.helse.flex.juridiskvurdering.juridiskVurderingTopic
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.kafka.AUDIT_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.personhendelse.AutomatiskInnsendingVedDodsfall
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.GrunnbeloepService
import no.nav.helse.flex.service.SykepengegrunnlagForNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.helse.flex.testdata.DatabaseReset
import no.nav.helse.flex.testoppsett.startAlleContainere
import no.nav.helse.flex.testoppsett.startMockWebServere
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.should
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import java.time.Instant
import kotlin.math.abs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableMockOAuth2Server
@SpringBootTest(classes = [Application::class])
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@AutoConfigureObservability
abstract class FellesTestOppsett : TestOppsettInterfaces {
    companion object {
        val pdlMockWebserver: MockWebServer
        val medlemskapMockWebServer: MockWebServer
        val sigrunMockWebServer: MockWebServer
        private val grunnbeloepApiMockWebServer: MockWebServer
        private val inntektskomponentenMockWebServer: MockWebServer
        private val eregMockWebServer: MockWebServer
        private val yrkesskadeMockWebServer: MockWebServer
        private val innsendingApiMockWebServer: MockWebServer
        val arbeidssokerregisterMockDispatcher: MockWebServer
        val brregMockWebServer: MockWebServer

        init {
            startAlleContainere()
            startMockWebServere().also {
                pdlMockWebserver = it.pdlMockWebserver
                medlemskapMockWebServer = it.medlemskapMockWebServer
                inntektskomponentenMockWebServer = it.inntektskomponentenMockWebServer
                eregMockWebServer = it.eregMockWebServer
                yrkesskadeMockWebServer = it.yrkesskadeMockWebServer
                innsendingApiMockWebServer = it.innsendingApiMockWebServer
                sigrunMockWebServer = it.pensjonsgivendeInntektMockWebServer
                grunnbeloepApiMockWebServer = it.grunnbeloepApiMockWebServer
                brregMockWebServer = it.brregMockWebServer
                arbeidssokerregisterMockDispatcher = it.arbeidssokerregisterMockDispatcher
            }
        }
    }

    override fun server(): MockOAuth2Server = server

    override fun mockMvc(): MockMvc = mockMvc

    override fun kafkaProducer(): KafkaProducer<String, String> = kafkaProducer

    @MockitoBean
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

    @MockitoBean
    lateinit var sykepengesoknadKvitteringerClient: SykepengesoknadKvitteringerClient

    @MockitoSpyBean
    lateinit var automatiskInnsendingVedDodsfall: AutomatiskInnsendingVedDodsfall

    @MockitoSpyBean
    lateinit var aivenKafkaProducer: AivenKafkaProducer

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var databaseReset: DatabaseReset

    @Autowired
    lateinit var server: MockOAuth2Server

    @Autowired
    lateinit var grunnbeloepService: GrunnbeloepService

    @MockitoSpyBean
    lateinit var grunnbeloepClient: GrunnbeloepClient

    @Autowired
    lateinit var sykepengesoknadKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var juridiskVurderingKafkaConsumer: Consumer<String, String>

    @Autowired
    @Qualifier("sykmeldingRetryProducer")
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var sykepengegrunnlagForNaeringsdrivende: SykepengegrunnlagForNaeringsdrivende

    @Autowired
    lateinit var auditlogKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var arbeidssokerregisterStoppConsumer: Consumer<String, String>

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

    @BeforeAll
    fun `Vi leser auditlog kafka topicet og feiler om noe eksisterer`() {
        auditlogKafkaConsumer.subscribeHvisIkkeSubscribed(AUDIT_TOPIC)
        auditlogKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser arbeidssokerregisterstopp kafka topicet og feiler om noe eksisterer`() {
        arbeidssokerregisterStoppConsumer.subscribeHvisIkkeSubscribed(ARBEIDSSOKERREGISTER_STOPP_TOPIC)
        arbeidssokerregisterStoppConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    fun hentJuridiskeVurderinger(antall: Int) =
        juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = antall)
            .tilJuridiskVurdering()

    @MockitoBean
    lateinit var enhetsregisterClient: EnhetsregisterClient

    @BeforeAll
    fun stubEnhetsregisterClient() {
        Mockito.`when`(enhetsregisterClient.erDagmamma(anyString())).thenReturn(DagmammaStatus.NEI)
    }
}

infix fun Instant.`should be within seconds of`(pair: Pair<Int, Instant>) = this.shouldBeWithinSecondsOf(pair.first.toInt() to pair.second)

infix fun Instant.shouldBeWithinSecondsOf(pair: Pair<Int, Instant>) {
    val (seconds, other) = pair
    val difference = abs(this.epochSecond - other.epochSecond)
    this.should { difference <= seconds }
}
