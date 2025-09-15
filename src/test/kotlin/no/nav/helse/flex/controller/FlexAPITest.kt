package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.aareg.Arbeidsforhold
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.domain.EventType
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidVedtakStatus
import no.nav.helse.flex.frisktilarbeid.Status
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidSoknad
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidVedtakDbRecord
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidVedtakStatus
import no.nav.helse.flex.jwt
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.net.URI
import java.time.LocalDate

class FlexAPITest : FellesTestOppsett() {
    val fnr = "12345678901"
    val fnrFlexer = "10987654321"
    lateinit var kafkaMelding: SykepengesoknadDTO

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeAll
    fun `Legger til søknader i databasen`() {
        kafkaMelding =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder = heltSykmeldt(),
                ),
            ).first()
    }

    @Test
    fun `Kan hente søknader og enkelt søknad fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/sykepengesoknader")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: FlexInternalResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.sykepengesoknadListe.shouldHaveSize(1)
        fraRest.klippetSykepengesoknadRecord.shouldHaveSize(0)
        fraRest.sykepengesoknadListe[0].id.shouldBeEqualTo(kafkaMelding.id)
        fraRest.sykepengesoknadListe[0].sykmeldingId.shouldBeEqualTo(kafkaMelding.sykmeldingId)

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` fnr
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter alle sykepengesoknader"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/sykepengesoknader")
                requestMethod `should be equal to` "POST"
            }
        }

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader/" + kafkaMelding.id)
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
            .let {
                val res: FlexInternalSoknadResponse = objectMapper.readValue(it.response.contentAsString)
                res.sykepengesoknad.id.shouldBeEqualTo(kafkaMelding.id)
            }

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` fnr
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter en sykepengesoknad"
                requestUrl `should be equal to` URI.create("/api/v1/flex/sykepengesoknader/${fraRest.sykepengesoknadListe[0].id}")
                requestMethod `should be equal to` "GET"
            }
        }
    }

    @Test
    fun `Kan hente enkelt søknad fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/sykepengesoknader")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: FlexInternalResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.sykepengesoknadListe.shouldHaveSize(1)
        fraRest.klippetSykepengesoknadRecord.shouldHaveSize(0)
        fraRest.sykepengesoknadListe[0].id.shouldBeEqualTo(kafkaMelding.id)
        fraRest.sykepengesoknadListe[0].sykmeldingId.shouldBeEqualTo(kafkaMelding.sykmeldingId)

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` fnr
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter alle sykepengesoknader"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/sykepengesoknader")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Andre apper kan ikke hente søknader`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan ikke hente søknader som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan hente identer fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/identer")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.HentIdenterRequest("211111111111").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: List<PdlIdent> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(3)
        fraRest[0] shouldBeEqualTo PdlIdent("FOLKEREGISTERIDENT", "211111111111")
        fraRest[1] shouldBeEqualTo PdlIdent("FOLKEREGISTERIDENT", "111111111111")
        fraRest[2] shouldBeEqualTo PdlIdent("AKTORID", "21111111111100")

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` "211111111111"
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter alle identer for ident"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/identer")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Kan hente aaregdata fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/aareg")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.HentAaregdataRequest("22222220001").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: List<Arbeidsforhold> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(2)

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` "22222220001"
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter aareg data"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/aareg")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Kan hente Sigrun-data fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/sigrun")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(
                            SoknadFlexAzureController
                                .HentPensjonsgivendeInntektRequest("22222220001", "2024")
                                .serialisertTilString(),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: HentPensjonsgivendeInntektResponse = objectMapper.readValue(result.response.contentAsString)
        fraRest.inntektsaar shouldBeEqualTo "2024"

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` "22222220001"
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter pensjonsgivende inntekt"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/sigrun")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Kan hente arbeidsøkerregisterdata fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/arbeidssokerregister")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.FnrRequest("22222220001").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val fraRest: List<ArbeidssokerperiodeResponse> = objectMapper.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(1)
        fraRest.first().startet.kilde shouldBeEqualTo "VEILEDER"

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` "22222220001"
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter arbeidssøkerregister status"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/arbeidssokerregister")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Kan hente friskmeldt vedtak fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/fta-vedtak-for-person")
                        .header(
                            "Authorization",
                            "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}",
                        ).content(SoknadFlexAzureController.FnrRequest("22222220001").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        result.response.contentAsString shouldBeEqualTo "[]"

        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` "22222220001"
                eventType `should be equal to` EventType.READ
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Henter friskmeldt til arbeidsformidling vedtak"
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/fta-vedtak-for-person")
                requestMethod `should be equal to` "POST"
            }
        }
    }

    @Test
    fun `Kan ikke hente identer som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/identer")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .content(SoknadFlexAzureController.HentIdenterRequest("211111111111").serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan slette FriskTilArbeid-søknad med status NY`() {
        val fnr = "11111111111"
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val nySoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.NY,
                fom = LocalDate.now().plusDays(5),
                tom = LocalDate.now().plusDays(9),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        slettSoknad(fnr, nySoknad, friskTilArbeidVedtakId)

        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).also {
            it.single().id `should be equal to` sendtSoknad.id
        }

        verifiserAuditLog(fnr, nySoknad)
    }

    @Test
    fun `Kan slette FriskTilArbeid-søknad med status FREMTIDIG`() {
        val fnr = "22222222222"
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        val fremtidigSoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.FREMTIDIG,
                fom = LocalDate.now().plusDays(5),
                tom = LocalDate.now().plusDays(9),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        sykepengesoknadDAO.lagreSykepengesoknad(fremtidigSoknad)

        slettSoknad(fnr, fremtidigSoknad, friskTilArbeidVedtakId)

        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).also {
            it.single().id `should be equal to` sendtSoknad.id
        }

        verifiserAuditLog(fnr, fremtidigSoknad)
    }

    @Test
    fun `Kan ikke slette FRISKMELDT_TIL_ARBEIDSFORMIDLING søknad med status SENDT`() {
        val fnr = "33333333333"
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET))

        val sendtSoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.SENDT,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        sykepengesoknadDAO.lagreSykepengesoknad(sendtSoknad)

        val result = slettSoknad(fnr, sendtSoknad, friskTilArbeidVedtakId)

        result.response.status `should be equal to` 400
        result.response.contentAsString `should be equal to` "Kan kun slette søknader med status NY eller FREMTIDIG."
    }

    @Test
    fun `Feiler når FriskTilArbeid-søknad ikke tilhører bruker`() {
        val fnr = "55555555555"
        val friskTilArbeidVedtakId = lagreFriskTilArbeidVedtakStatus(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET))

        val nySoknad =
            lagFriskTilArbeidSoknad(
                fnr = fnr,
                friskTilArbeidVedtakId = friskTilArbeidVedtakId,
                soknadStatus = Soknadstatus.NY,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(4),
            ).also { sykepengesoknadDAO.lagreSykepengesoknad(it) }

        sykepengesoknadDAO.lagreSykepengesoknad(nySoknad)

        val result = slettSoknad("66666666666", nySoknad, friskTilArbeidVedtakId)

        result.response.status `should be equal to` 404
        result.response.contentAsString `should be equal to` "Fant ikke søknad med id: ${nySoknad.id} tilhørende den aktuelle brukeren."
    }

    private fun lagreFriskTilArbeidVedtakStatus(friskTilArbeidVedtakStatus: FriskTilArbeidVedtakStatus): String =
        friskTilArbeidRepository.save(lagFriskTilArbeidVedtakDbRecord(friskTilArbeidVedtakStatus)).id!!

    private fun slettSoknad(
        fnr: String,
        nySoknad: Sykepengesoknad,
        friskTilArbeidVedtakId: String,
    ): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("/api/v1/flex/fta-soknad")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                    .content(
                        SoknadFlexAzureController
                            .SlettSykepengesoknadRequest(fnr, nySoknad.id)
                            .serialisertTilString(),
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andReturn()

    private fun verifiserAuditLog(
        fnr: String,
        nySoknad: Sykepengesoknad,
    ) {
        auditlogKafkaConsumer.ventPåRecords(1).first().let {
            val auditEntry: AuditEntry = objectMapper.readValue(it.value())
            with(auditEntry) {
                appNavn `should be equal to` "flex-internal"
                utførtAv `should be equal to` fnrFlexer
                oppslagPå `should be equal to` fnr
                eventType `should be equal to` EventType.DELETE
                forespørselTillatt `should be` true
                beskrivelse `should be equal to` "Sletter sykepengesoknad av type FRISKMELDT_TIL_ARBEIDSFORMIDLING med id: ${nySoknad.id}."
                requestUrl `should be equal to` URI.create("http://localhost/api/v1/flex/fta-soknad")
                requestMethod `should be equal to` "DELETE"
            }
        }
    }
}
