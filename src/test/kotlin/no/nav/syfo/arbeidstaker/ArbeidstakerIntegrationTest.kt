package no.nav.syfo.arbeidstaker

import no.nav.helse.flex.sykepengesoknad.kafka.MerknadDTO
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.syfo.*
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.ANSVARSERKLARING
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.testutil.SoknadBesvarer
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be null`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class ArbeidstakerIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    final val fnr = "12454578474"
    final val basisdato = LocalDate.of(2021, 9, 1)

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    fun `1 - arbeidstakersøknader opprettes for en lang sykmelding`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = basisdato.minusDays(20),
            tom = basisdato.plusDays(15),
            merknader = listOf(Merknad(type = "UGYLDIG_TILBAKEDATERING", beskrivelse = "Hey"))
        )
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[1].status).isEqualTo(RSSoknadstatus.NY)
        assertThat(hentetViaRest[0].merknaderFraSykmelding!!.first().type).isEqualTo("UGYLDIG_TILBAKEDATERING")
        assertThat(hentetViaRest[0].merknaderFraSykmelding!!.first().beskrivelse).isEqualTo("Hey")

        val ventPåRecords = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(2))
        val kafkaSoknader = ventPåRecords.tilSoknader()

        assertThat(kafkaSoknader).hasSize(2)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(kafkaSoknader[0].merknaderFraSykmelding!!.first()).isEqualTo(
            MerknadDTO(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = "Hey"
            )
        )
        assertThat(kafkaSoknader[1].status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(kafkaSoknader[1].sendTilGosys).isNull()
        assertThat(kafkaSoknader[1].merknader).isNull()
    }

    @Test
    fun `1,5 - Vi kan ikke korrigere en soknad som ikke er sendt`() {
        val soknaden = hentSoknader(fnr)[0]
        korrigerSoknadMedResult(soknaden.id, fnr).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn()
    }

    @Test
    fun `2 - søknadene har forventa spørsmål`() {
        val soknader = hentSoknader(fnr)

        assertThat(soknader[0].sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "FRAVAR_FOR_SYKMELDINGEN",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "JOBBET_DU_100_PROSENT_0",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER",
                "UTDANNING",
                "PERMITTERT_NAA",
                "PERMITTERT_PERIODE",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )

        assertThat(soknader[1].sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "JOBBET_DU_100_PROSENT_0",
                "ANDRE_INNTEKTSKILDER",
                "UTDANNING",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )

        assertThat(soknader[0].sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")
    }

    @Test
    fun `3 - vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "PERMITTERT_NAA", svar = "NEI")
            .besvarSporsmal(tag = "PERMITTERT_PERIODE", svar = "NEI")
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "FRAVAR_FOR_SYKMELDINGEN_NAR",
                svar = """{"fom":"${soknaden.fom!!.minusDays(14)}","tom":"${soknaden.fom!!.minusDays(7)}"}"""
            )
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
        kafkaSoknader[0].arbeidUtenforNorge!!.`should be false`()
        assertThat(kafkaSoknader[0].fravarForSykmeldingen).isEqualTo(
            listOf(
                PeriodeDTO(
                    fom = soknaden.fom!!.minusDays(14),
                    tom = soknaden.fom!!.minusDays(7)
                )
            )
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `5 - vi besvarer og sender inn søknad nummer 2`() {
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknaden = hentSoknader(fnr).find { it.status == RSSoknadstatus.NY }!!
        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "JOBBET_DU_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER", svar = "NEI")
            .besvarSporsmal(tag = "UTDANNING", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        assertThat(kafkaSoknader[0].sendTilGosys).isNull()
        assertThat(kafkaSoknader[0].merknader).isNull()
        kafkaSoknader[0].arbeidUtenforNorge.`should be null`()

        assertThat(kafkaSoknader[0].merknaderFraSykmelding!!.first()).isEqualTo(
            MerknadDTO(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = "Hey"
            )
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `6 - ingen søknader opprettes for bekreftet arbeidstakersøknad (strengt fortrolig adddresse)`() {
        sykepengesoknadDAO.nullstillSoknader(fnr)

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_BEKREFTET,
            arbeidsgiver = null
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = basisdato.minusDays(20),
            tom = basisdato.plusDays(15),
        )
            .copy(harRedusertArbeidsgiverperiode = true)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr)
        assertThat(hentetViaRest).hasSize(0)
    }
}
