package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import no.nav.helse.flex.*
import no.nav.helse.flex.client.sykmeldinger.SykmeldingerResponse
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.FlexSykmeldingMockDispatcher
import no.nav.helse.flex.testdata.lagSykmeldingsPerioder
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
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
    }

    @Test
    fun `Oppretter ventetidssøknad for selvstendig næringsdrivende`() {
        val kafkaMessage =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                fom = LocalDate.of(2024, 1, 1),
            )
        val kafkaMessage1 =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                fom = kafkaMessage.sykmelding.tom!!.plusDays(15),
            )

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessage)))

        sendSykmelding(
            sykmeldingKafkaMessage = kafkaMessage,
            oppfolgingsdato = dato,
            forventaSoknader = 0,
            erUtenforVentetid = false,
        )

        hentSoknader(fnr).size `should be equal to` 0

        sendSykmelding(
            sykmeldingKafkaMessage = kafkaMessage1,
            oppfolgingsdato = dato,
            forventaSoknader = 2,
            erUtenforVentetid = true,
            sykmeldingIderMedSammeVentetid = setOf(kafkaMessage.sykmelding.id),
        )

        hentSoknader(fnr).run {
            this.size `should be equal to` 2
            this.first { it.sykmeldingId == kafkaMessage.sykmelding.id }.ventetidSykmeldingUuid `should be equal to`
                kafkaMessage1.sykmelding.id
            this.first { it.sykmeldingId == kafkaMessage1.sykmelding.id }.ventetidSykmeldingUuid `should be equal to` null
        }
    }

    @Test
    fun `Oppretter ikke ventetidssoknad for selvstendig næringsdrivende hvis feil kastes`() {
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)

        mockFlexSyketilfelleVentetidForSykmeldingKasterFeil(sykmeldingId = kafkaMessage.sykmelding.id)

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
    fun `Dupliserer ikke søknad dersom forrige sykmeldingen trigget opprettelse av begge søknader`() {
        val kafkaMessage =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                fom = LocalDate.of(2024, 1, 1),
            )
        val kafkaMessage1 =
            lagSykmeldingKafkaMessage(
                fnr = fnr,
                fom = kafkaMessage.sykmelding.tom!!.plusDays(15),
            )

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessage, kafkaMessage1)))

        sendSykmelding(
            sykmeldingKafkaMessage = kafkaMessage,
            oppfolgingsdato = dato,
            forventaSoknader = 2,
            erUtenforVentetid = true,
            sykmeldingIderMedSammeVentetid = setOf(kafkaMessage1.sykmelding.id),
        )

        val hentSoknader = hentSoknader(fnr)
        hentSoknader.size `should be equal to` 2

        sendSykmelding(
            sykmeldingKafkaMessage = kafkaMessage1,
            oppfolgingsdato = dato,
            forventaSoknader = 0,
            erUtenforVentetid = true,
            sykmeldingIderMedSammeVentetid = setOf(kafkaMessage.sykmelding.id),
        )

        val hentSoknader1 = hentSoknader(fnr)
        hentSoknader1.size `should be equal to` 2
        hentSoknader.map { it.id } `should be equal to` hentSoknader1.map { it.id }
    }

    @Test
    fun `Oppretter søknader i riktig rekkefølge basert på fom`() {
        val kafkaMessageFørste = lagSykmeldingKafkaMessage(fnr = fnr, fom = dato)
        val kafkaMessageSiste = lagSykmeldingKafkaMessage(fnr = fnr, fom = dato.plusDays(10))

        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(kafkaMessageFørste, kafkaMessageSiste)))

        sendSykmelding(
            sykmeldingKafkaMessage = kafkaMessageSiste,
            oppfolgingsdato = dato,
            forventaSoknader = 2,
            erUtenforVentetid = true,
            sykmeldingIderMedSammeVentetid = setOf(kafkaMessageFørste.sykmelding.id),
        )

        val hentSoknader = hentSoknader(fnr)
        hentSoknader.size `should be equal to` 2
        hentSoknader[0].fom `should be equal to` dato
        hentSoknader[1].fom `should be equal to` dato.plusDays(10)
    }

    private fun lagSykmeldingKafkaMessage(
        fnr: String,
        fom: LocalDate = dato,
    ): SykmeldingKafkaMessageDTO {
        val statusDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = statusDTO.event.sykmeldingId,
                sykmeldingsperioder = lagSykmeldingsPerioder(fom = fom, tom = fom.plusDays(15)),
            )
        return SykmeldingKafkaMessageDTO(
            sykmelding = sykmelding,
            event = statusDTO.event,
            kafkaMetadata = statusDTO.kafkaMetadata,
        )
    }
}
