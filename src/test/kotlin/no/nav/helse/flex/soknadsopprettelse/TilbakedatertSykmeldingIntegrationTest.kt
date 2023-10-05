package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.Merknad
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.Mockito.times

@TestMethodOrder(MethodOrderer.MethodName::class)
class TilbakedatertSykmeldingIntegrationTest : BaseTestClass() {

    private val fnr = "123456789"

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
    fun `oppretter ikke søknad for sykmelding under behandling når feature switch er av`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = skapArbeidsgiverSykmelding(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        )
            .copy(
                merknader = listOf(Merknad(type = "UNDER_BEHANDLING", beskrivelse = "Manuell behandling :("))
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `oppretter søknad for sykmelding under behandling når featureswitch er på`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-soknader-for-tilbakedaterte-sykmeldinger-under-behandling")

        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = skapArbeidsgiverSykmelding(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        )
            .copy(
                merknader = listOf(Merknad(type = "UNDER_BEHANDLING", beskrivelse = "Manuell behandling :("))
            )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        val records = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        records.first().merknaderFraSykmelding!!.shouldHaveSize(1)
        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
    }
}
