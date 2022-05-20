package no.nav.helse.flex.behandlingsdager

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.avbrytSoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.gjenapneSoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.korrigerSoknad
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.AVBRUTT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.NY
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.*
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BehandligsdagerIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    final val fnr = "123456789"

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    @Test
    @Order(1)
    fun `behandingsdagsøknad opprettes for en lang sykmelding`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)

        val sykepengesoknaderFraDb = sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr))

        assertThat(sykepengesoknaderFraDb.size).isEqualTo(1)
        assertThat(sykepengesoknaderFraDb[0]).isInstanceOf(Sykepengesoknad::class.java)
        assertThat(sykepengesoknaderFraDb[0].soknadstype).isEqualTo(Soknadstype.BEHANDLINGSDAGER)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(NY)
    }

    @Test
    @Order(2)
    fun `behandlingsdager har riktig formattert spørsmålstekst`() {

        val soknaden = hentSoknader(fnr).first()
        val uke0 = soknaden.sporsmal!!.first { it.tag == "ENKELTSTAENDE_BEHANDLINGSDAGER_0" }.undersporsmal[0]
        val uke1 = soknaden.sporsmal!!.first { it.tag == "ENKELTSTAENDE_BEHANDLINGSDAGER_0" }.undersporsmal[1]

        uke0.sporsmalstekst `should be equal to` "01.01.2018 - 05.01.2018"
        uke1.sporsmalstekst `should be equal to` "08.01.2018 - 10.01.2018"
    }

    @Test
    @Order(3)
    fun `vi svarer på søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        // Svar på søknad
        val rsSykepengesoknad = hentSoknader(fnr).first()

        SoknadBesvarer(rSSykepengesoknad = rsSykepengesoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRAVER_FOR_BEHANDLING", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .besvarSporsmal(
                tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0",
                svar = "${rsSykepengesoknad.fom}",
                ferdigBesvart = false
            )
            .besvarSporsmal(
                tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1",
                svarListe = emptyList(),
                ferdigBesvart = false
            )
            .also {
                sendSoknadMedResult(fnr, rsSykepengesoknad.id)
                    .andExpect(((MockMvcResultMatchers.status().isBadRequest)))
            }
            .besvarSporsmal(tag = "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1", svar = "${rsSykepengesoknad.tom}")
            .sendSoknad()

        val soknadPaKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().first()

        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(soknadPaKafka.fnr).isEqualTo("123456789")
        assertThat(soknadPaKafka.behandlingsdager).isEqualTo(listOf(rsSykepengesoknad.fom, rsSykepengesoknad.tom))

        val refreshedSoknad = hentSoknader(fnr).first()
        assertThat(refreshedSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        assertThat(refreshedSoknad.sporsmal!!.find { it.tag == ANSVARSERKLARING }!!.svar[0].verdi).isEqualTo("CHECKED")
        assertThat(refreshedSoknad.sporsmal!!.find { it.tag == BEKREFT_OPPLYSNINGER }!!.svar[0].verdi).isEqualTo("CHECKED")

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(4)
    fun `vi korrigerer søknaden`() {
        val soknaden = hentSoknader(fnr).first()

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        // Korriger søknad
        val korrigerSoknad = korrigerSoknad(soknadId = soknaden.id, fnr = fnr)
        assertThat(korrigerSoknad.status).isEqualTo(RSSoknadstatus.UTKAST_TIL_KORRIGERING)
        assertThat(korrigerSoknad.sporsmal!!.find { it.tag == ANSVARSERKLARING }!!.svar.size).isEqualTo(0)
        assertThat(korrigerSoknad.sporsmal!!.find { it.tag == BEKREFT_OPPLYSNINGER }!!.svar.size).isEqualTo(0)
        assertThat(korrigerSoknad.korrigerer).isEqualTo(soknaden.id)

        SoknadBesvarer(rSSykepengesoknad = korrigerSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        val soknadPaKafka = kafkaSoknader.last()
        assertThat(soknadPaKafka.status).isEqualTo(SoknadsstatusDTO.SENDT)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(5)
    fun `vi kan opprette, avbryte og gjenåpne søknad`() {

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2018, 1, 1),
                    tom = LocalDate.of(2018, 1, 10),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        // Avbryt søknad
        avbrytSoknad(soknadId = soknad.id, fnr = fnr)
        val avbruttSoknad = hentSoknader(fnr).first { it.id == soknad.id }
        assertThat(avbruttSoknad.status).isEqualTo(RSSoknadstatus.AVBRUTT)

        val avbruttPåKafka = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader().last()

        assertThat(avbruttPåKafka.status).isEqualTo(AVBRUTT)

        // Gjenåpne søknad
        gjenapneSoknad(soknadId = soknad.id, fnr = fnr)
        val gjenapnetSoknad = hentSoknader(fnr).first { it.id == avbruttSoknad.id }
        assertThat(gjenapnetSoknad.status).isEqualTo(RSSoknadstatus.NY)
        assertThat(gjenapnetSoknad.sporsmal?.sumOf { it.svar.size }).isEqualTo(0)

        val soknadPaKafka2 = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().last()
        assertThat(soknadPaKafka2.status).isEqualTo(NY)

        // Svar på avbrutt søknad er ikke med på kafka
        val ansvarserklaring = avbruttPåKafka.sporsmal!!.first { it.tag == ANSVARSERKLARING }
        assertThat(ansvarserklaring.svar).isEmpty()
    }

    @Test
    @Order(6)
    fun `kombinert behandlingsdag og vanlig splittes riktig`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 3, 1),
                    tom = LocalDate.of(2020, 3, 15),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = 1,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                ),
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 3, 16),
                    tom = LocalDate.of(2020, 3, 31),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(soknader).hasSize(2)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[0].fom).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(soknader[0].tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(soknader[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(soknader[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[1].fom).isEqualTo(LocalDate.of(2020, 3, 16))
        assertThat(soknader[1].tom).isEqualTo(LocalDate.of(2020, 3, 31))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(7)
    fun `to behandlingsdager-perioder splittes riktig`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId).copy(
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 3, 1),
                    tom = LocalDate.of(2020, 3, 15),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = 1,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                ),
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 3, 16),
                    tom = LocalDate.of(2020, 3, 31),
                    type = PeriodetypeDTO.BEHANDLINGSDAGER, reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = 1,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(soknader).hasSize(2)
        assertThat(soknader[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[0].fom).isEqualTo(LocalDate.of(2020, 3, 1))
        assertThat(soknader[0].tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(soknader[1].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(soknader[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(soknader[1].fom).isEqualTo(LocalDate.of(2020, 3, 16))
        assertThat(soknader[1].tom).isEqualTo(LocalDate.of(2020, 3, 31))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }
}
