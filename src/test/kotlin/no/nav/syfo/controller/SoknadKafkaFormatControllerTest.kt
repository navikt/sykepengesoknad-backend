package no.nav.syfo.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.*
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.soknadsopprettelse.genererSykepengesoknadFraMetadata
import no.nav.syfo.soknadsopprettelse.sorterSporsmal
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.tilOsloInstant
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*

class SoknadKafkaFormatControllerTest : BaseTestClass() {

    @Autowired
    private lateinit var opprettSoknadService: OpprettSoknadService

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    fun skapSoknad(): Sykepengesoknad {
        val opprettSykepengesoknadFraSortertMetadata = genererSykepengesoknadFraMetadata(
            SoknadMetadata(
                fnr = "f-745463060",
                status = Soknadstatus.NY,
                startSykeforlop = LocalDate.now().minusMonths(1),
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusMonths(1).plusDays(8),
                soknadstype = Soknadstype.ARBEIDSTAKERE,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                arbeidsgiverOrgnummer = "987654321",
                arbeidsgiverNavn = "ARBEIDSGIVER A/S",
                sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
                sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.now().minusMonths(1),
                        tom = LocalDate.now().minusMonths(1).plusDays(4),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            ),
            emptyList()
        ).sorterSporsmal()
        opprettSoknadService.lagreOgPubliserSøknad(opprettSykepengesoknadFraSortertMetadata, emptyList())
        return opprettSykepengesoknadFraSortertMetadata
    }

    @Test
    fun `Vi kan hente en søknad på samme format som kafka topicet med version 2 token`() {
        val soknad = skapSoknad()
        val result = mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/${soknad.id}/kafkaformat")
                    .header("Authorization", "Bearer ${skapAzureJwt()}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

        val fraRest = OBJECT_MAPPER.readValue<SykepengesoknadDTO>(result.response.contentAsString)

        assertThat(fraRest.fnr).isEqualTo(soknad.fnr)

        val fraKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()

        fun SykepengesoknadDTO.fjernMs(): SykepengesoknadDTO = this.copy(
            opprettet = opprettet?.truncatedTo(SECONDS),
            sykmeldingSkrevet = sykmeldingSkrevet?.truncatedTo(SECONDS)
        )

        fraRest.fjernMs().shouldBeEqualTo(fraKafka.fjernMs())
    }

    @Test
    fun `Vi får 401 om vi ikke sender auth header`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized).andReturn()
    }

    @Test
    fun `Vi får 401 om vi har jibberish i auth headeren`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "Bearer sdafsdaf")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized).andReturn()

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "dsgfdgdfgf")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized).andReturn()
    }

    @Test
    fun `Vi får 403 om vi har feil subject`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "Bearer ${skapAzureJwt("facebook")}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden).andReturn()
    }

    @Test
    fun `Vi får 404 om vi ikke finner søknaden`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/e65e14ad-35eb-4695-add9-9f26ba120818/kafkaformat")
                    .header("Authorization", "Bearer ${skapAzureJwt()}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isNotFound).andReturn()
    }
}
