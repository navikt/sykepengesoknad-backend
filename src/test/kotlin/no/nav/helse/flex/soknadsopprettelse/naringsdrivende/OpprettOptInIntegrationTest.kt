package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.testdata.lagSykmeldingsPerioder
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.OffsetDateTime

class OpprettOptInIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val dato = LocalDate.of(2025, 1, 1)

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer.reset()
    }

    @AfterEach
    fun tearDown() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @Test
    fun `Oppretter søknad via opt-in endepunkt for næringsdrivende`() {
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr, Arbeidssituasjon.NAERINGSDRIVENDE)

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
        )

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
            oppfolgingsdato = dato,
        )

        val token = server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isOk)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        val soknader = hentSoknader(fnr)
        soknader.size `should be equal to` 1
        soknader.first().sykmeldingId `should be equal to` kafkaMessage.sykmelding.id
    }

    @Test
    fun `Oppretter søknad via opt-in endepunkt for frilanser`() {
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr, Arbeidssituasjon.FRILANSER)

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
        )

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
            oppfolgingsdato = dato,
        )

        val token = server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isOk)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        val soknader = hentSoknader(fnr)
        soknader.size `should be equal to` 1
        soknader.first().sykmeldingId `should be equal to` kafkaMessage.sykmelding.id
    }

    @Test
    fun `Uautorisert klient får 403`() {
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr, Arbeidssituasjon.NAERINGSDRIVENDE)

        val token = server.tokenxToken(fnr = fnr, clientId = "en-annen-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `Gir 400 dersom status ikke er bekreftet`() {
        val kafkaMessage =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                statusEvent = STATUS_SENDT,
            )

        val token = server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun `Gir 400 dersom mottattTidspunkt er eldre enn 4 måneder`() {
        val gammelMottattTidspunkt = OffsetDateTime.now().minusMonths(4).minusDays(2)
        val kafkaMessage =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                mottattTidspunkt = gammelMottattTidspunkt,
            )

        val token = server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun `Gir 400 dersom arbeidssituasjon ikke er frilanser eller naeringsdrivende`() {
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)

        val token = server.tokenxToken(fnr = fnr, clientId = "flex-sykmeldinger-backend-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/v2/soknader/opprett-opt-in")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(kafkaMessage.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    private fun lagSykmeldingKafkaMessage(
        fnr: String,
        arbeidssituasjon: Arbeidssituasjon,
        statusEvent: String = STATUS_BEKREFTET,
        mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    ): SykmeldingKafkaMessageDTO {
        val statusDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                arbeidssituasjon = arbeidssituasjon,
                statusEvent = statusEvent,
            )
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = statusDTO.event.sykmeldingId,
                sykmeldingsperioder = lagSykmeldingsPerioder(fom = dato, tom = dato.plusDays(15)),
                mottattTidspunkt = mottattTidspunkt,
            )
        return SykmeldingKafkaMessageDTO(
            sykmelding = sykmelding,
            event = statusDTO.event,
            kafkaMetadata = statusDTO.kafkaMetadata,
        )
    }
}
