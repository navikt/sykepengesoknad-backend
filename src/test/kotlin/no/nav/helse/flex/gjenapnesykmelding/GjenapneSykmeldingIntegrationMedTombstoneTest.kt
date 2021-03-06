package no.nav.helse.flex.gjenapnesykmelding

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class GjenapneSykmeldingIntegrationMedTombstoneTest : BaseTestClass() {

    private final val fnr = "123456789"
    private final val timestamp = OffsetDateTime.now()

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    @Test
    fun `1 - 3 arbeidsledigsøknader opprettes for fire sykmeldinger`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            statusEvent = STATUS_BEKREFTET,
            arbeidsgiver = null,
            timestamp = timestamp
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val sykmelding2 = UUID.randomUUID().toString()
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleSykeforloep(sykmelding2)

        val sykmeldingKafkaMessage2 = SykmeldingKafkaMessage(
            sykmelding = sykmelding.copy(id = sykmelding2),
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding2, sykmeldingKafkaMessage2)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSLEDIG)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.NY)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `2 - vi svarer på den ene søknaden`() {

        val rsSykepengesoknad = hentSoknader(fnr).first()
        SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRISKMELDT", svar = "JA")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "ARBEIDSLEDIG_UTLAND", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        val soknadPaKafka = soknader.last()
        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
    }

    @Test
    fun `3 - vi sender inn tombstone status på den sendte og den ene nye sykmeldingene - Kun den ene blir slettet`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(2)
        val sykmedlingIdTilNy = soknader.find { it.status == RSSoknadstatus.NY }!!.sykmeldingId!!
        val sykmedlingIdTilSendt = soknader.find { it.status == RSSoknadstatus.SENDT }!!.sykmeldingId!!

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId = sykmedlingIdTilNy,
            sykmeldingKafkaMessage = null
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId = sykmedlingIdTilSendt,
            sykmeldingKafkaMessage = null
        )

        val soknaderEtterEvents = hentSoknader(fnr)

        assertThat(soknaderEtterEvents).hasSize(1)
        assertThat(soknaderEtterEvents.find { it.status == RSSoknadstatus.SENDT }!!.sykmeldingId).isEqualTo(
            sykmedlingIdTilSendt
        )

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()
        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SLETTET)
        assertThat(soknadPaKafka.sykmeldingId).isEqualTo(sykmedlingIdTilNy)
    }

    @Test
    fun `4 - en arbeidstakersøknad kan ikke slettes fra kafka`() {
        val sykmelding4 = UUID.randomUUID().toString()
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmelding4).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )
        mockFlexSyketilfelleSykeforloep(sykmelding4)

        val skapSykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgNavn = "Nav", orgnummer = "123454321"),
            timestamp = timestamp
        )

        val sykmeldingKafkaMessage4 = SykmeldingKafkaMessage(
            sykmelding = sykmelding.copy(id = sykmelding4),
            event = skapSykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = skapSykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding4, sykmeldingKafkaMessage4)

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(2)
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(
            sykmeldingId = sykmelding4,
            sykmeldingKafkaMessage = null
        )

        val soknaderEtterApenMelding = hentSoknader(fnr)
        assertThat(soknaderEtterApenMelding).hasSize(2)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }
}
