package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.fakes.FlexSykmeldingerClientFake
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleErUtenforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.mockFlexSyketilfelleSykmeldingerIsykeforloep
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.ventPåRecords
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
        val kafkaMessage = lagSykmeldingKafkaMessage(fnr)
        val kafkaMessage1 = lagSykmeldingKafkaMessage(fnr)

        flexSykmeldingerClientFake.leggTilSykmelding(kafkaMessage)

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

        mockFlexSyketilfelleSykmeldingerIsykeforloep(
            sykmeldingIder = setOf(kafkaMessage.sykmelding.id),
        )

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

    private fun lagSykmeldingKafkaMessage(fnr: String): SykmeldingKafkaMessage {
        val statusDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmelding = skapArbeidsgiverSykmelding(sykmeldingId = statusDTO.event.sykmeldingId)
        return SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = statusDTO.event,
            kafkaMetadata = statusDTO.kafkaMetadata,
        )
    }
}
