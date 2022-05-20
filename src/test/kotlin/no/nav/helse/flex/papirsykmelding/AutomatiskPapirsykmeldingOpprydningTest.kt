package no.nav.helse.flex.papirsykmelding

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.*
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class AutomatiskPapirsykmeldingOpprydningTest : BaseTestClass() {

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    companion object {
        const val fnr = "123456789"
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.of(2020, 3, 15),
        )
    }

    @Test
    fun `1 - arbeidstakersøknader opprettes for en lang sykmelding`() {

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(3)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3).tilSoknader()

        assertThat(soknader).hasSize(3)
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(NY)
    }

    @Test
    fun `2 - vi sender inn den ene søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val førsteSøknad = hentSoknader(fnr)
            .find { it.fom == LocalDate.of(2020, 1, 1) }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = førsteSøknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "PERMITTERT_NAA", svar = "NEI")
            .besvarSporsmal(tag = "PERMITTERT_PERIODE", svar = "NEI")
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "NEI")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "JOBBET_DU_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(soknader).hasSize(1)
        assertThat(soknader[0].status).isEqualTo(SENDT)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `3 - vi mottar identisk sykmelding igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(3)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.SENDT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    fun `4 - vi mottar en korrigert sykmelding med litt lengre periode, sendt blir korreigert og søknadene opprettes på nytt`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.of(2020, 4, 15),
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 6).tilSoknader()

        assertThat(soknader[0].status).isEqualTo(SLETTET)
        assertThat(soknader[1].status).isEqualTo(SLETTET)
        assertThat(soknader[2].status).isEqualTo(NY)
        assertThat(soknader[3].status).isEqualTo(NY)
        assertThat(soknader[4].status).isEqualTo(NY)
        assertThat(soknader[5].status).isEqualTo(NY)
    }

    @Test
    fun `5 - vi mottar den korrigerte sykmeldingen igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.of(2020, 4, 15),
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    fun `6 - sykmeldingen korrigeres igjen, men må med annen sykmeldingsgrad`() {
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.of(2020, 4, 15),
            type = PeriodetypeDTO.GRADERT,
            gradert = GradertDTO(grad = 33, reisetilskudd = false),
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(5)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.KORRIGERT)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[2].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[3].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[4].status).isEqualTo(RSSoknadstatus.NY)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 8).tilSoknader()

        assertThat(soknader[0].status).isEqualTo(SLETTET)
        assertThat(soknader[1].status).isEqualTo(SLETTET)
        assertThat(soknader[2].status).isEqualTo(SLETTET)
        assertThat(soknader[3].status).isEqualTo(SLETTET)
        assertThat(soknader[4].status).isEqualTo(NY)
        assertThat(soknader[5].status).isEqualTo(NY)
        assertThat(soknader[6].status).isEqualTo(NY)
        assertThat(soknader[7].status).isEqualTo(NY)
    }
}
