package no.nav.syfo.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.domain.Arbeidssituasjon.FRILANSER
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.kafka.consumer.RebehandlingSykmeldingSendt
import no.nav.syfo.mockFlexSyketilfelleErUtaforVentetid
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.util.serialisertTilString
import no.nav.syfo.ventPåRecords
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.Acknowledgment
import java.time.LocalDate

class RebehandlingSoknadopprettelseTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var rebehandlingSykmeldingSendt: RebehandlingSykmeldingSendt

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
    }

    @AfterEach
    fun tearDown() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    private val fnr = "123456789"
    private val topic = "syfosoknad-rebehandle-sykmelding"
    private var sykmeldingId = ""

    @Test
    fun `Rebehandler sykmelding for Arbeidstaker`() {
        val cr = skapConsumerRecord()
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        rebehandlingSykmeldingSendt.listen(cr, acknowledgment)
        val sykepengesoknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingId)
        assertThat(sykepengesoknader.size).isEqualTo(1)
        assertThat(sykepengesoknader.first().status).isEqualTo(NY)
        assertThat(sykepengesoknader.first().arbeidssituasjon).isEqualTo(ARBEIDSTAKER)
        verify(aivenKafkaProducer, times(1)).produserMelding(any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Rebehandler sykmelding for Frilanser`() {

        val cr = skapConsumerRecord(
            statusEvent = STATUS_BEKREFTET,
            arbeidssituasjon = FRILANSER
        )

        mockFlexSyketilfelleErUtaforVentetid(sykmeldingId, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        rebehandlingSykmeldingSendt.listen(
            cr,
            acknowledgment
        )
        val sykepengesoknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingId)
        assertThat(sykepengesoknader.size).isEqualTo(1)
        assertThat(sykepengesoknader.first().status).isEqualTo(NY)
        assertThat(sykepengesoknader.first().arbeidssituasjon).isEqualTo(FRILANSER)
        verify(aivenKafkaProducer, times(1)).produserMelding(any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Ved UventetArbeidssituasjonException så legges sykmelding tilbake til rebehandling`() {
        rebehandlingSykmeldingSendt.listen(
            skapConsumerRecord(
                statusEvent = STATUS_SENDT,
                arbeidssituasjon = FRILANSER
            ),
            acknowledgment
        )
        val sykepengesoknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingId)
        assertThat(sykepengesoknader.size).isEqualTo(0)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    private fun skapConsumerRecord(
        statusEvent: String = STATUS_SENDT,
        arbeidssituasjon: Arbeidssituasjon = ARBEIDSTAKER
    ): ConsumerRecord<String, String> {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            statusEvent = statusEvent,
            arbeidssituasjon = arbeidssituasjon,
            arbeidsgiver = ArbeidsgiverStatusDTO("123", "456", "Jobb")
        )
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 2, 5),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                ),
                syketilfelleStartDato = LocalDate.of(2020, 2, 1)
            )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
        return ConsumerRecord(topic, 1, 1L, sykmeldingId, sykmeldingKafkaMessage.serialisertTilString())
    }
}
