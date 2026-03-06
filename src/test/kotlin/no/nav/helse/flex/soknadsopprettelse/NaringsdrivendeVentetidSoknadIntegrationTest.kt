package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.*
import no.nav.helse.flex.client.sykmeldinger.SykmeldingerResponse
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.FlexSykmeldingMockDispatcher
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpServerErrorException
import java.time.LocalDate

class NaringsdrivendeVentetidSoknadIntegrationTest : FellesTestOppsett() {
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
    fun `Oppretter ventetidssøknad for selvstendig næringsdrivende`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage.sykmelding.id,
            erUtenforVentetid = false,
        )
        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage1.sykmelding.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
            oppfolgingsdato = dato,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage1.sykmelding.id, kafkaMessage.sykmelding.id),
        )

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessage)))

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        hentSoknader(fnr).size `should be equal to` 0

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage1.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage1,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        hentSoknader(fnr).size `should be equal to` 2
    }

    @Test
    fun `Oppretter ikke ventetidssoknad for selvstendig næringsdrivende hvis toggle er på og feil kastes`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage.sykmelding.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidKasterFeil(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
        )

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
            oppfolgingsdato = dato,
        )

        assertThrows<HttpServerErrorException.InternalServerError> {
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )
        }

        hentSoknader(fnr).size `should be equal to` 0
    }

    @Test
    fun `Oppretter ikke ventetidssoknad for selvstendig næringsdrivende hvis toggle er av selv om feil kastes`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage.sykmelding.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidKasterFeil(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
        )

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
            oppfolgingsdato = dato,
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        hentSoknader(fnr).size `should be equal to` 1
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Oppretter ikke ventetidssoknad for selvstendig næringsdrivende hvis toggle er av`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage.sykmelding.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
        )

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessage1)))

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
            oppfolgingsdato = dato,
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        hentSoknader(fnr).size `should be equal to` 1
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Dupliserer ikke søknad dersom forrige sykmeldingen trigget opprettelse av begge søknader`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage.sykmelding.id,
            erUtenforVentetid = true,
        )
        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = kafkaMessage1.sykmelding.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
        )
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(kafkaMessage1.sykmelding.id, kafkaMessage.sykmelding.id),
        )

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessage, kafkaMessage1)))

        // flex-syketilfelle kjenner til begge sykmeldingene når første sykmelding behandles
        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
            oppfolgingsdato = dato,
        )
        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id, kafkaMessage1.sykmelding.id),
            oppfolgingsdato = dato,
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        val hentSoknader = hentSoknader(fnr)
        hentSoknader.size `should be equal to` 2
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage1.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage1,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        val hentSoknader1 = hentSoknader(fnr)
        hentSoknader1.size `should be equal to` 2
        hentSoknader.map { it.id } `should be equal to` hentSoknader1.map { it.id }
    }

    fun lagSykmeldingKafkaMessage(fnr: String): SykmeldingKafkaMessage {
        val statusDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmelding = skapArbeidsgiverSykmelding(sykmeldingId = statusDTO.event.sykmeldingId)
        return SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = statusDTO.event,
            kafkaMetadata = statusDTO.kafkaMetadata,
        )
    }
}
