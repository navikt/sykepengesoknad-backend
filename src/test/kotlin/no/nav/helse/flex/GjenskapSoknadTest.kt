package no.nav.helse.flex

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.GjenskapSoknadConsumer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.AktiverEnkeltSoknadService
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GjenskapSoknadTest : BaseTestClass() {

    private val fnr = "123456789"
    private val sykmeldingIdSomKlippes = "5aec579e-2c82-4a69-aaab-cb251b287dfb"
    private lateinit var gjenskapbarKafkaMelding: SykmeldingKafkaMessage
    private val offsetNow = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS)

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var gjenskapSoknadConsumer: GjenskapSoknadConsumer

    @Autowired
    private lateinit var aktiverEnkeltSoknadService: AktiverEnkeltSoknadService

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    @Test
    @Order(1)
    fun `Sykmelding 1`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(
                "123",
                "321",
                orgNavn = "enToTre",
            ),
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2022, 8, 18),
            tom = LocalDate.of(2022, 8, 31)
        ).copy(behandletTidspunkt = offsetNow.minusDays(3))

        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(2)
    fun `Sykmelding 2`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(
                "123",
                "321",
                orgNavn = "enToTre",
            ),
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2022, 8, 18),
            tom = LocalDate.of(2022, 9, 1)
        ).copy(behandletTidspunkt = offsetNow.minusDays(2))

        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(3)
    fun `Søknadene ble aktivert`() {
        sykepengesoknadDAO
            .finnSykepengesoknader(listOf(fnr))
            .forEach { aktiverEnkeltSoknadService.aktiverSoknad(it.id) }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(4)
    fun `Sykmelding 3`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            sykmeldingId = sykmeldingIdSomKlippes,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(
                "123",
                "321",
                orgNavn = "enToTre",
            ),
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2022, 8, 20),
            tom = LocalDate.of(2022, 9, 1)
        ).copy(behandletTidspunkt = offsetNow.minusDays(1))

        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        gjenskapbarKafkaMelding = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId,
            gjenskapbarKafkaMelding
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(5)
    fun `Sykmelding 3 ble klippet`() {
        val soknad = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingIdSomKlippes).first()
        soknad.fom shouldBeEqualTo LocalDate.of(2022, 9, 1)
        soknad.tom shouldBeEqualTo LocalDate.of(2022, 9, 1)
        aktiverEnkeltSoknadService.aktiverSoknad(soknad.id)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(6)
    fun `Bruker avbryter sykmelding 1 og 2 som ikke skal brukes`() {
        sykepengesoknadDAO
            .finnSykepengesoknader(listOf(fnr))
            .filter { it.fom != it.tom }
            .forEach { avbrytSoknad(it.id, fnr) }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(6)
    fun `Søknaden gjenskapes`() {
        mockFlexSyketilfelleSykeforloep(sykmeldingIdSomKlippes)

        gjenskapSoknadConsumer.gjenskapSoknad(
            id = sykmeldingIdSomKlippes,
            melding = gjenskapbarKafkaMelding,
        )

        val soknad = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingIdSomKlippes).first()
        soknad.fom shouldBeEqualTo LocalDate.of(2022, 8, 20)
        soknad.tom shouldBeEqualTo LocalDate.of(2022, 9, 1)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }
}
