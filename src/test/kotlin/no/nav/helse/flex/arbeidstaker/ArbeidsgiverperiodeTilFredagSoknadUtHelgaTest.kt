package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.client.narmesteleder.Forskuttering
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.juridiskvurdering.Utfall
import no.nav.helse.flex.mockArbeidsgiverForskutterer
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.sykepengesoknad.kafka.MottakerDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilJuridiskVurdering
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidsgiverperiodeTilFredagSoknadUtHelgaTest : BaseTestClass() {

    private final val fnr = "12345674152"
    private final val fredagen = LocalDate.of(2021, 12, 17)

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
        databaseReset.resetDatabase()
    }

    @Test
    fun `vi besvarer og sender inn en søknad som bare går til arbeidsgiver siden perioden slutta på en fredag, men søknaden gikk til søndag`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = fredagen.minusDays(14),
            tom = fredagen.plusDays(2)
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(1)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(1))

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = fredagen.minusDays(14), tom = fredagen)
            )
        )
        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
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

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(kafkaSoknader[0].mottaker).isEqualTo(MottakerDTO.ARBEIDSGIVER)

        val vurdering = juridiskVurderingKafkaConsumer
            .ventPåRecords(antall = 4)
            .tilJuridiskVurdering()
            .first { it.paragraf == "8-11" }

        vurdering.paragraf `should be equal to` "8-11"
        vurdering.utfall `should be equal to` Utfall.VILKAR_IKKE_OPPFYLT
        vurdering.input `should be equal to` mapOf(
            "versjon" to "2022-02-01",
            "sykepengesoknadTom" to "2021-12-19",
            "arbeidsgiverperiode" to mapOf(
                "fom" to "2021-12-03",
                "tom" to "2021-12-17",
            ),
        )
        vurdering.output `should be equal to` mapOf(
            "versjon" to "2022-02-01",
            "kunHelgEtterArbeidsgiverperiode" to true,
        )
    }

    @Test
    fun `vi besvarer og sender inn en søknad som går til arbeidsgiver og NAV siden perioden slutta på en fredag, men søknaden gikk til mandag`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = fredagen.minusDays(14),
            tom = fredagen.plusDays(3)
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(1)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(1))

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = fredagen.minusDays(14), tom = fredagen)
            )
        )
        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
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

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(kafkaSoknader[0].mottaker).isEqualTo(MottakerDTO.ARBEIDSGIVER_OG_NAV)

        val vurderinger = juridiskVurderingKafkaConsumer.ventPåRecords(antall = 3).tilJuridiskVurdering()

        vurderinger.find { it.paragraf == "8-11" }.`should be null`()
    }
}
