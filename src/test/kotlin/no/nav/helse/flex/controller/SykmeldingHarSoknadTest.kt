package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.RSHarSoknadForSykmeldingResponse
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.fakes.SoknadLagrerFake
import no.nav.helse.flex.testutil.lagSoknad
import no.nav.helse.flex.tokenxToken
import no.nav.helse.flex.util.objectMapper
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

class SykmeldingHarSoknadTest : FakesTestOppsett() {
    @Autowired
    private lateinit var soknadLagrer: SoknadLagrerFake

    private val fnr = "12345678901"

    @Test
    fun `returnerer harSoknad=true når søknad finnes for sykmelding`() {
        val sykmeldingUuid = UUID.randomUUID().toString()

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = fnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )

        val response = hentHarSoknadForSykmelding(sykmeldingUuid, fnr)
        response.harSoknad `should be equal to` true
    }

    @Test
    fun `returnerer harSoknad=false når ingen søknad finnes for sykmelding`() {
        val response = hentHarSoknadForSykmelding(UUID.randomUUID().toString(), fnr)
        response.harSoknad `should be equal to` false
    }

    @Test
    fun `returnerer 403 når søknad tilhører annen bruker`() {
        val sykmeldingUuid = UUID.randomUUID().toString()
        val annetFnr = "99999999999"

        soknadLagrer.lagreSoknad(
            lagSoknad(
                fnr = annetFnr,
                arbeidsgiver = 1,
                fom = LocalDate.now().minusDays(14),
                tom = LocalDate.now(),
                startSykeforlop = LocalDate.now().minusDays(14),
                arbeidsSituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                soknadsType = Soknadstype.ARBEIDSTAKERE,
                status = Soknadstatus.NY,
                sykmeldingId = sykmeldingUuid,
            ),
        )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v2/soknader/sykmelding/$sykmeldingUuid/harSoknad")
                    .header(
                        "Authorization",
                        "Bearer ${server.tokenxToken(fnr = fnr, clientId = "ditt-sykefravaer-frontend-client-id")}",
                    ).contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `returnerer 401 uten token`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v2/soknader/sykmelding/${UUID.randomUUID()}/harSoknad")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isUnauthorized)
    }

    private fun hentHarSoknadForSykmelding(
        sykmeldingUuid: String,
        fnr: String,
        clientId: String = "ditt-sykefravaer-frontend-client-id",
    ): RSHarSoknadForSykmeldingResponse {
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v2/soknader/sykmelding/$sykmeldingUuid/harSoknad")
                        .header(
                            "Authorization",
                            "Bearer ${server.tokenxToken(fnr = fnr, clientId = clientId)}",
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        return objectMapper.readValue(json)
    }
}
