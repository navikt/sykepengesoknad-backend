package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.fakes.FlexSykmeldingerClientFake
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleErUtenforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class NaringsdrivendeVentetidSoknadIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val dato = LocalDate.of(2025, 1, 1)

    @Autowired
    private lateinit var flexSykmeldingerClientFake: FlexSykmeldingerClientFake

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
    fun `Oppretter ventetidssøknad for selvstendig næringsdrivende`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        val sykmeldingStatusKafkaMessageDTO1 = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId1 = sykmeldingStatusKafkaMessageDTO1.event.sykmeldingId
        val sykmelding1 =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId1).copy(harRedusertArbeidsgiverperiode = true)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        flexSykmeldingerClientFake.leggTilSykmelding(sykmeldingKafkaMessage)

        val sykmeldingKafkaMessage1 =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding1,
                event = sykmeldingStatusKafkaMessageDTO1.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO1.kafkaMetadata,
            )

        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = sykmelding.id,
            erUtenforVentetid = false,
        )
        mockFlexSyketilfelleErUtenforVentetid(
            sykmeldingId = sykmelding1.id,
            erUtenforVentetid = true,
        )

        mockFlexSyketilfelleSykeforloep(sykmeldingId = sykmeldingId1, oppfolgingsdato = dato)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        hentSoknader(fnr).size `should be equal to` 0

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId1,
            sykmeldingKafkaMessage = sykmeldingKafkaMessage1,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        hentSoknader(fnr).size `should be equal to` 2
    }
}
