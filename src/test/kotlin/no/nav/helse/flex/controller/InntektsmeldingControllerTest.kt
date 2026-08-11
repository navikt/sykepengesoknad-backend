package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SoknadLagrerImpl
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.testutil.lagSoknad
import no.nav.helse.flex.tokenxToken
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.kafka.NAV_CALLID
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.*

class InntektsmeldingControllerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var soknadLagrer: SoknadLagrerImpl

    @BeforeEach
    fun setupEach() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Skal kunne hente soknader fra inntektsmelding-API med Tokenx`() {
        val sykepengesoknadUuid = UUID.randomUUID().toString()
        lagreSoknad(sykepengesoknadUuid)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/arbeidsgiver/soknader")
                    .content(
                        HentSoknaderRequest(
                            fnr = "11111111111",
                            eldsteFom = LocalDate.parse("2026-01-01"),
                            orgnummer = "org-1",
                        ).serialisertTilString(),
                    ).header(
                        "Authorization",
                        "Bearer ${
                            server.tokenxToken(
                                fnr = "22222222222",
                                clientId = "spinntekstmelding-frontend-client-id",
                            )
                        }",
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andReturn()
            .let { objectMapper.readValue<List<HentSoknaderResponse>>(it.response.contentAsString) }
            .also {
                it.size shouldBeEqualTo 1
                it.single().sykepengesoknadUuid shouldBeEqualTo sykepengesoknadUuid
            }
    }

    @Test
    fun `Feiler med ved henting av soknader fra inntektsmelding-API med TokenX hvis client-id er feil`() {
        val sykepengesoknadUuid = UUID.randomUUID().toString()
        lagreSoknad(sykepengesoknadUuid)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v1/arbeidsgiver/soknader")
                    .content(
                        HentSoknaderRequest(
                            fnr = "11111111111",
                            eldsteFom = LocalDate.parse("2026-01-01"),
                            orgnummer = "org-1",
                        ).serialisertTilString(),
                    ).header(
                        "Authorization",
                        "Bearer ${
                            server.tokenxToken(
                                fnr = "22222222222",
                                clientId = "ukjent-client-id",
                            )
                        }",
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `Skal kunne hente soknader fra inntektsmelding-API med Azure-token`() {
        val sykepengesoknadUuid = UUID.randomUUID().toString()
        lagreSoknad(sykepengesoknadUuid)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/arbeidsgiver/soknader")
                    .content(
                        HentSoknaderRequest(
                            fnr = "11111111111",
                            eldsteFom = LocalDate.parse("2026-01-01"),
                            orgnummer = "org-1",
                        ).serialisertTilString(),
                    ).header("Authorization", "Bearer ${skapAzureJwt("im-soeknad-client-id")}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andReturn()
            .let { objectMapper.readValue<List<HentSoknaderResponse>>(it.response.contentAsString) }
            .also {
                it.size shouldBeEqualTo 1
                it.single().sykepengesoknadUuid shouldBeEqualTo sykepengesoknadUuid
            }
    }

    @Test
    fun `Feiler ved henting av soknader fra inntektsmelding-API med Azure-token hvis client-id er feil`() {
        val sykepengesoknadUuid = UUID.randomUUID().toString()
        lagreSoknad(sykepengesoknadUuid)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/arbeidsgiver/soknader")
                    .content(
                        HentSoknaderRequest(
                            fnr = "11111111111",
                            eldsteFom = LocalDate.parse("2026-01-01"),
                            orgnummer = "org-1",
                        ).serialisertTilString(),
                    ).header("Authorization", "Bearer ${skapAzureJwt("ukjent-client-id")}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isForbidden)
    }

    private fun lagreSoknad(sykepengesoknadUuid: String) {
        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = "11111111111",
                id = sykepengesoknadUuid,
                arbeidsgiver = 1,
                fom = LocalDate.parse("2026-08-01"),
                tom = LocalDate.parse("2026-08-30"),
                startSykeforlop = LocalDate.parse("2028-08-01"),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
            ),
        )
    }
}
