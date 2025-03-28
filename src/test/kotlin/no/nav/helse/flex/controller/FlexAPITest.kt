package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.*
import no.nav.helse.flex.client.aareg.Arbeidsforhold
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.domain.EventType
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.net.URI

class FlexAPITest : FellesTestOppsett() {
    val fnr = "12345678901"
    val fnrFlexer = "10987654321"
    lateinit var kafkaMelding: SykepengesoknadDTO

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
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
            )
            .andExpect(MockMvcResultMatchers.status().isOk).andReturn().let {
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
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentSykepengesoknaderRequest(fnr).serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
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
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan hente identer fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/identer")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentIdenterRequest("211111111111").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentAaregdataRequest("22222220001").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
    fun `Kan hente sigrundata fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/flex/sigrun")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentPensjonsgivendeInntektRequest("22222220001", "2024").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", fnrFlexer)}")
                        .content(SoknadFlexAzureController.HentSisteArbeidssokerperiode("22222220001").serialisertTilString())
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

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
    fun `Kan ikke hente identer som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/flex/identer")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .content(SoknadFlexAzureController.HentIdenterRequest("211111111111").serialisertTilString())
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }
}
