// file: src/test/kotlin/no/nav/helse/flex/papirsykmelding/AutomatiskPapirsykmeldingOpprydningTest.kt
package no.nav.helse.flex.papirsykmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.*
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class AutomatiskPapirsykmeldingOpprydningTest : FellesTestOppsett() {
    private val fnr = "12345678900"

    private val sykmeldingStatusKafkaMessageDTO =
        skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "Gatekjøkkenet"),
        )

    private val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId

    private val sykmelding =
        skapArbeidsgiverSykmelding(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.of(2020, 3, 15),
        )

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
        clearAllKafkaMessages()
    }

    @AfterEach
    fun tearDown() {
        clearAllKafkaMessages()
    }

    private fun clearAllKafkaMessages() {
        try {
            // Clear all relevant kafka consumers
            sykepengesoknadKafkaConsumer.hentProduserteRecords()
            juridiskVurderingKafkaConsumer.hentProduserteRecords()

            // Add any other kafka consumers that might have messages
            try {
                auditlogKafkaConsumer.hentProduserteRecords()
            } catch (e: Exception) {
                // Ignore if this consumer doesn't exist or fails
            }

            // Small delay to ensure all messages are processed
            Thread.sleep(100)

            // Try once more to clear any remaining messages
            sykepengesoknadKafkaConsumer.hentProduserteRecords()
            juridiskVurderingKafkaConsumer.hentProduserteRecords()
        } catch (e: Exception) {
            println("Warning: Failed to clear kafka messages: ${e.message}")
        }
    }

    @Test
    fun `1 - arbeidstakersøknader opprettes for en lang sykmelding`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(3, Duration.ofSeconds(10)).tilSoknader()

        assertThat(soknader).hasSize(3)
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(NY)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(3)
    }

    @Test
    fun `2 - vi sender inn den ene søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val førsteSøknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.fom == LocalDate.of(2020, 1, 1) }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = førsteSøknad, testOppsettInterfaces = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .oppsummering()
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(1, Duration.ofSeconds(10)).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader[0].status).isEqualTo(SENDT)
    }

    @Test
    fun `3 - vi mottar identisk sykmelding igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        // Wait briefly and verify no messages are produced
        Thread.sleep(1000)
        val messages = sykepengesoknadKafkaConsumer.hentProduserteRecords()
        assertThat(messages).hasSize(0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(3)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.SENDT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `4 - vi mottar en korrigert sykmelding med litt lengre periode, sendt blir korreigert og søknadene opprettes på nytt`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val korrigertSykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 4, 15),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = korrigertSykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(6, Duration.ofSeconds(15)).tilSoknader()

        assertThat(soknader[0].status).isEqualTo(SLETTET)
        assertThat(soknader[1].status).isEqualTo(SLETTET)
        assertThat(soknader[2].status).isEqualTo(NY)
        assertThat(soknader[3].status).isEqualTo(NY)
        assertThat(soknader[4].status).isEqualTo(NY)
        assertThat(soknader[5].status).isEqualTo(NY)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `5 - vi mottar den korrigerte sykmeldingen igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val korrigertSykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 4, 15),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = korrigertSykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        // Wait briefly and verify no messages are produced
        Thread.sleep(1000)
        val messages = sykepengesoknadKafkaConsumer.hentProduserteRecords()
        assertThat(messages).hasSize(0)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `6 - sykmeldingen korrigeres igjen, men må med annen sykmeldingsgrad`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val gradertSykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = LocalDate.of(2020, 1, 1),
                tom = LocalDate.of(2020, 4, 15),
                type = PeriodetypeDTO.GRADERT,
                gradert = GradertDTO(grad = 33, reisetilskudd = false),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = gradertSykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(8, Duration.ofSeconds(15)).tilSoknader()

        assertThat(soknader[0].status).isEqualTo(SLETTET)
        assertThat(soknader[1].status).isEqualTo(SLETTET)
        assertThat(soknader[2].status).isEqualTo(SLETTET)
        assertThat(soknader[3].status).isEqualTo(SLETTET)
        assertThat(soknader[4].status).isEqualTo(NY)
        assertThat(soknader[5].status).isEqualTo(NY)
        assertThat(soknader[6].status).isEqualTo(NY)
        assertThat(soknader[7].status).isEqualTo(NY)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)
    }
}
