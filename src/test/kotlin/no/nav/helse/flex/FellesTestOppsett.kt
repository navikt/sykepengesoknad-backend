package no.nav.helse.flex

import io.getunleash.FakeUnleash
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.client.kvitteringer.SykepengesoknadKvitteringerClient
import no.nav.helse.flex.juridiskvurdering.JURIDISK_VURDERING_TOPIC
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.kafka.AUDIT_TOPIC
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.mockdispatcher.FellesQueueDispatcher
import no.nav.helse.flex.personhendelse.AutomatiskInnsendingVedDodsfall
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.SykepengegrunnlagForNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import no.nav.helse.flex.testdata.DatabaseReset
import no.nav.helse.flex.testoppsett.MockWebServere
import no.nav.helse.flex.testoppsett.startAlleContainere
import no.nav.helse.flex.testoppsett.startMockWebServere
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.should
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
        private val mockWebServere: MockWebServere = startMockWebServere()
        private val eregMockWebServer
            get() = mockWebServere.eregMockWebServer
        private val innsendingApiMockWebServer
            get() = mockWebServere.innsendingApiMockWebServer
        private val inntektskomponentenMockWebServer
            get() = mockWebServere.inntektskomponentenMockWebServer
        private val yrkesskadeMockWebServer
            get() = mockWebServere.yrkesskadeMockWebServer
        val aaregMockWebServer
            get() = mockWebServere.aaregMockWebServer
        val arbeidssokerregisterMockWebServer
            get() = mockWebServere.arbeidssokerregisterMockWebServer
        val brregMockWebServer
            get() = mockWebServere.brregMockWebServer
        val medlemskapMockWebServer
            get() = mockWebServere.medlemskapMockWebServer
        val pdlMockWebserver
            get() = mockWebServere.pdlMockWebserver
        val sigrunMockWebServer
            get() = mockWebServere.pensjonsgivendeInntektMockWebServer
        val enhetsregisterMockWebServer
            get() = mockWebServere.enhetsregisterMockWebServer

        init {
            startAlleContainere()
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
    fun `Vi resetter databasen`() {
        databaseReset.resetDatabase()
    }

    @AfterAll
    fun `Disable unleash toggles`() {
        fakeUnleash.disableAll()
    }

    @BeforeAll
    fun abonnerPåKafkaTopicene() {
        sykepengesoknadKafkaConsumer.subscribeHvisIkkeSubscribed(SYKEPENGESOKNAD_TOPIC)
        juridiskVurderingKafkaConsumer.subscribeHvisIkkeSubscribed(JURIDISK_VURDERING_TOPIC)
        auditlogKafkaConsumer.subscribeHvisIkkeSubscribed(AUDIT_TOPIC)
        arbeidssokerregisterStoppConsumer.subscribeHvisIkkeSubscribed(ARBEIDSSOKERREGISTER_STOPP_TOPIC)
    }

    @AfterAll
    fun `Vi leser alle topics og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        sykepengesoknadKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        juridiskVurderingKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        auditlogKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        arbeidssokerregisterStoppConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi sjekker om det er noen responser igjen i rest service serverne`() {
        FellesQueueDispatcher.alle().forEach { dispatcher ->
            check(!dispatcher.harRequestsIgjen()) {
                "Det er noen requests igjen i dispatcher ${dispatcher::class.simpleName}"
            }.also {
                dispatcher.clearQueue()
            }
        }
    }

    fun hentJuridiskeVurderinger(antall: Int) =
        juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = antall)
            .tilJuridiskVurdering()
}

infix fun Instant.`should be within seconds of`(pair: Pair<Int, Instant>) = this.shouldBeWithinSecondsOf(pair.first.toInt() to pair.second)

infix fun Instant.shouldBeWithinSecondsOf(pair: Pair<Int, Instant>) {
    val (seconds, other) = pair
    val difference = abs(this.epochSecond - other.epochSecond)
    this.should { difference <= seconds }
}
