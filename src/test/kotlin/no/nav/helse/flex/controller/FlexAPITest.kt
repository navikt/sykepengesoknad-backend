// file: src/test/kotlin/no/nav/helse/flex/controller/FlexAPITest.kt
package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.*
import no.nav.helse.flex.client.aareg.Arbeidsforhold
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.domain.EventType
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ThreadLocalRandom

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.Random::class)
class FlexAPITest : FellesTestOppsett() {
    private val fnrFlexer = genererTestFnr()

    private lateinit var deltFnr: String
    private lateinit var deltKafkamelding: SykepengesoknadDTO

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeAll
    fun initDeltData() {
        databaseReset.resetDatabase()
        mockFlexSyketilfelleSykeforloep(emptyList())

        deltFnr = genererTestFnr()
        deltKafkamelding =
            sendSykmelding(
                sykmeldingKafkaMessage(fnr = deltFnr, sykmeldingsperioder = heltSykmeldt()),
                forventaSoknader = 1,
            ).first()
    }

    @BeforeEach
    fun setupEach() {
        mockFlexSyketilfelleSykeforloep(emptyList())
    }

    private fun genererTestFnr(): String = (10000000000L + ThreadLocalRandom.current().nextLong(9_000_000_000L)).toString()

    // ------- READ TESTS (bruker delt data) --------

    @Test
    fun `Kan hente flere søknader fra flex-internal-frontend`() {
        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(deltFnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: FlexInternalResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.sykepengesoknadListe.shouldHaveSize(1)
        fraRest.sykepengesoknadListe[0].id.shouldBeEqualTo(deltKafkamelding.id)

        verifiserAuditLog(
            fnr = deltFnr,
            eventType = EventType.READ,
            beskrivelse = "Henter alle sykepengesoknader",
            requestUrl = URI.create("/api/v1/flex/sykepengesoknader"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan hente enkelt søknad med GET`() {
        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader/${deltKafkamelding.id}")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: FlexInternalSoknadResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.sykepengesoknad.id.shouldBeEqualTo(deltKafkamelding.id)

        verifiserAuditLog(
            fnr = deltFnr,
            eventType = EventType.READ,
            beskrivelse = "Henter en sykepengesoknad",
            requestUrl = URI.create("/api/v1/flex/sykepengesoknader/${fraRest.sykepengesoknad.id}"),
            requestMethod = "GET",
        )
    }

    @Test
    fun `Andre apper kan ikke hente søknader`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(deltFnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan ikke hente søknader som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${jwt(deltFnr)}")
                    .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(deltFnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan hente identer`() {
        val testIdent = "234567111"

        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/identer")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.HentIdenterRequest(testIdent).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: List<PdlIdent> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(3)

        verifiserAuditLog(
            fnr = testIdent,
            eventType = EventType.READ,
            beskrivelse = "Henter alle identer for ident",
            requestUrl = URI.create("/api/v1/flex/identer"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan hente aaregdata`() {
        val testIdent = "22222220001"

        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/aareg")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.HentAaregdataRequest(testIdent).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: List<Arbeidsforhold> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(2)

        verifiserAuditLog(
            fnr = testIdent,
            eventType = EventType.READ,
            beskrivelse = "Henter aareg data",
            requestUrl = URI.create("/api/v1/flex/aareg"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan hente Sigrun-data`() {
        val testIdent = "22222220001"

        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sigrun")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.HentPensjonsgivendeInntektRequest(testIdent, "2024").serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: HentPensjonsgivendeInntektResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.inntektsaar shouldBeEqualTo "2024"

        verifiserAuditLog(
            fnr = testIdent,
            eventType = EventType.READ,
            beskrivelse = "Henter pensjonsgivende inntekt",
            requestUrl = URI.create("/api/v1/flex/sigrun"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan hente arbeidsøkerregisterdata`() {
        val testIdent = "22222220001"

        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/arbeidssokerregister")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.FnrRequest(testIdent).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val fraRest: List<ArbeidssokerperiodeResponse> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(1)

        verifiserAuditLog(
            fnr = testIdent,
            eventType = EventType.READ,
            beskrivelse = "Henter arbeidssøkerregister status",
            requestUrl = URI.create("/api/v1/flex/arbeidssokerregister"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan hente friskmeldt vedtak`() {
        val testIdent = "22222220001"

        val result =
            performOkRequest(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/fta-vedtak-for-person")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.FnrRequest(testIdent).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )

        result.response.contentAsString shouldBeEqualTo "[]"

        verifiserAuditLog(
            fnr = testIdent,
            eventType = EventType.READ,
            beskrivelse = "Henter friskmeldt til arbeidsformidling vedtak",
            requestUrl = URI.create("/api/v1/flex/fta-vedtak-for-person"),
            requestMethod = "POST",
        )
    }

    @Test
    fun `Kan ikke hente identer som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/identer")
                    .header("Authorization", "Bearer ${jwt(deltFnr)}")
                    .content(SoknadFlexAzureController.HentIdenterRequest("211111111111").serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    // ------- DESTRUCTIVE TESTS (disse bruker kun genererTestFnr for å ikke dele data) --------

    @Test
    @Transactional
    fun `Kan slette FriskTilArbeid-søknad med status NY`() {
        val testFnr = genererTestFnr()
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(testFnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val nySoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.NY,
                fom = LocalDate.now().plusDays(5),
                tom = LocalDate.now().plusDays(9),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        slettSoknad(testFnr, nySoknad)

        sykepengesoknadDAO.finnSykepengesoknader(listOf(testFnr)).also {
            it.single().id `should be equal to` sendtSoknad.id
        }

        verifiserAuditLog(
            fnr = testFnr,
            eventType = EventType.DELETE,
            beskrivelse = "Sletter sykepengesoknad av type FRISKMELDT_TIL_ARBEIDSFORMIDLING med id: ${nySoknad.id}.",
            requestUrl = URI.create("http://localhost/api/v1/flex/fta-soknad"),
            requestMethod = "DELETE",
        )
    }

    @Test
    @Transactional
    fun `Kan slette FriskTilArbeid-søknad med status FREMTIDIG`() {
        val testFnr = genererTestFnr()
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(testFnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val fremtidigSoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.FREMTIDIG,
                fom = LocalDate.now().plusDays(5),
                tom = LocalDate.now().plusDays(9),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        slettSoknad(testFnr, fremtidigSoknad)

        sykepengesoknadDAO.finnSykepengesoknader(listOf(testFnr)).also {
            it.single().id `should be equal to` sendtSoknad.id
        }

        verifiserAuditLog(
            fnr = testFnr,
            eventType = EventType.DELETE,
            beskrivelse = "Sletter sykepengesoknad av type FRISKMELDT_TIL_ARBEIDSFORMIDLING med id: ${fremtidigSoknad.id}.",
            requestUrl = URI.create("http://localhost/api/v1/flex/fta-soknad"),
            requestMethod = "DELETE",
        )
    }

    @Test
    @Transactional
    fun `Kan ikke slette FRISKMELDT_TIL_ARBEIDSFORMIDLING søknad med status SENDT`() {
        val testFnr = genererTestFnr()
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(testFnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val result = slettSoknad(testFnr, sendtSoknad)

        result.response.status `should be equal to` 400
        result.response.contentAsString `should be equal to` "Kan kun slette søknader med status NY eller FREMTIDIG."
    }

    @Test
    @Transactional
    fun `Feiler når FriskTilArbeid-søknad ikke tilhører bruker`() {
        val testFnr = genererTestFnr()
        val otherFnr = genererTestFnr()
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(testFnr, Status.FATTET))

        val nySoknad =
            lagFriskTilArbeidSoknad(
                fnr = testFnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.NY,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val result = slettSoknad(otherFnr, nySoknad)

        result.response.status `should be equal to` 404
        result.response.contentAsString `should be equal to` "Fant ikke søknad med id: ${nySoknad.id} tilhørende den aktuelle brukeren."
    }

    // ------- Helpers --------
    private fun performOkRequest(requestBuilder: MockHttpServletRequestBuilder): MvcResult =
        mockMvc.perform(requestBuilder).andExpect(MockMvcResultMatchers.status().isOk).andReturn()

    private fun lagreFriskTilArbeidVedtakStatus(s: FriskTilArbeidVedtakStatus): String =
        friskTilArbeidRepository.save(lagFriskTilArbeidVedtakDbRecord(s)).id!!

    private fun slettSoknad(
        fnr: String,
        soknad: Sykepengesoknad,
    ): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("/api/v1/flex/fta-soknad")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(SoknadFlexAzureController.SlettSykepengesoknadRequest(fnr, soknad.id).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andReturn()

    private fun verifiserAuditLog(
        fnr: String,
        utfortAv: String = fnrFlexer,
        eventType: EventType,
        beskrivelse: String,
        requestUrl: URI,
        requestMethod: String,
    ) {
        val records = auditlogKafkaConsumer.ventPåRecords(1, Duration.ofSeconds(5))

        val entry = objectMapper.readValue<AuditEntry>(records.single().value())
        with(entry) {
            appNavn shouldBeEqualTo "flex-internal"
            utførtAv shouldBeEqualTo utfortAv
            oppslagPå shouldBeEqualTo fnr
            this.eventType shouldBeEqualTo eventType
            forespørselTillatt shouldBe true
            this.beskrivelse shouldBeEqualTo beskrivelse
            this.requestUrl.path shouldBeEqualTo requestUrl.path
            this.requestMethod shouldBeEqualTo requestMethod
        }
    }
}
