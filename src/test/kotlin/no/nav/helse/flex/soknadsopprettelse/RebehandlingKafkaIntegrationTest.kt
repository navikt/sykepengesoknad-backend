package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

class RebehandlingKafkaIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val dato = LocalDate.of(2025, 1, 1)

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
    }

    @AfterEach
    fun tearDown() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Legger til rebehandling når arbeidssituasjonen ikke er forventet, UventetArbeidssituasjonException`() {
        val statusDto =
            skapKafkaMelding(
                statusEvent = STATUS_SENDT,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
            )

        prosesserOgVerifiserRebehandling(statusDto)
    }

    @Test
    fun `Legger til rebehandling når arbeidsgiver mangler, ManglerArbeidsgiverException`() {
        val statusDto = skapKafkaMelding(arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER, arbeidsgiver = null)

        prosesserOgVerifiserRebehandling(statusDto) {
            mockFlexSyketilfelleSykeforloep(id, dato)
        }
    }

    private fun prosesserOgVerifiserRebehandling(
        statusDto: SykmeldingStatusKafkaMessageDTO,
        forberedProsessering: ArbeidsgiverSykmeldingDTO.() -> Unit = {},
    ) {
        val sykmelding = skapSykmeldingDTO(statusDto)
        sykmelding.forberedProsessering()
        val kafkaMessage = skapSykmeldingKafkaMessage(statusDto, sykmelding)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            kafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
        verifiserRebehandling()
    }

    private fun skapSykmeldingKafkaMessage(
        statusDto: SykmeldingStatusKafkaMessageDTO,
        sykmelding: ArbeidsgiverSykmeldingDTO,
    ) = SykmeldingKafkaMessageDTO(
        sykmelding = sykmelding,
        event = statusDto.event,
        kafkaMetadata = statusDto.kafkaMetadata,
    )

    private fun verifiserRebehandling() {
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
    }

    private fun skapKafkaMelding(
        statusEvent: String = STATUS_SENDT,
        arbeidssituasjon: Arbeidssituasjon,
        arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    ): SykmeldingStatusKafkaMessageDTO =
        skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            statusEvent = statusEvent,
            arbeidssituasjon = arbeidssituasjon,
            arbeidsgiver = arbeidsgiver,
        )

    private fun skapSykmeldingDTO(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO,
        syketilfelleStartDato: LocalDate? = LocalDate.of(2020, 2, 1),
        sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> =
            listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 2, 5),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null,
                ),
            ),
    ) = skapArbeidsgiverSykmelding(
        sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
    ).copy(
        sykmeldingsperioder = sykmeldingsperioder,
        syketilfelleStartDato = syketilfelleStartDato,
    )
}
