package no.nav.syfo.julesoknad

import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.syfo.BaseTestClass
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus.*
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockArbeidsgiverForskutterer
import no.nav.syfo.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.repository.JulesoknadkandidatDAO
import no.nav.syfo.service.AktiverService
import no.nav.syfo.service.JulesoknadService
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiverService: AktiverService

    @Autowired
    private lateinit var julesoknadService: JulesoknadService

    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    final val fnr = "123456789"
    final val aktorid = fnr + "00"

    final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        narmestelederMockRestServiceServer?.reset()
        flexSyketilfelleMockRestServiceServer?.reset()
        evictAllCaches()
        databaseReset.resetDatabase()
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode aktiveres med en gang`() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15)
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode med tom ut i januar aktiveres med en gang`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 5),
            tom = LocalDate.of(nesteÅr + 1, 1, 1)
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `14 dagers arbeidsledig søknad i riktig periode aktiveres ikke`() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 14)
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `15 dagers arbeidsledig søknad i med for sen fom aktiveres ikke`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 8),
            tom = LocalDate.of(nesteÅr, 12, 22)
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode uten forskuttering aktiveres med en gang`() {
        val orgnummer = "999999999"
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer)
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med ukjent forskuttering aktiveres med en gang`() {
        val orgnummer = "999999999"
        mockArbeidsgiverForskutterer(Forskuttering.UKJENT, orgnummer)
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).isEmpty()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med forskuttering aktiveres ikke med en gang`() {
        val orgnummer = "999999999"
        mockArbeidsgiverForskutterer(Forskuttering.JA, orgnummer)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )

        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        val kandidater = julesoknadkandidatDAO.hentJulesoknadkandidater()
        assertThat(kandidater).hasSize(1)
        assertThat(kandidater.first().sykepengesoknadUuid).isEqualTo(soknaden.id)

        // Vi endrer nærmestelederskjema og kjører cronjobben
        narmestelederMockRestServiceServer?.reset()
        evictAllCaches()
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer)

        julesoknadService.prosseserJulesoknadKandidater()

        val soknaderEtterCronjob = hentSoknader(fnr)
        assertThat(soknaderEtterCronjob).hasSize(1)

        val soknadenEtterCronjob = soknaderEtterCronjob.first()
        assertThat(soknadenEtterCronjob.status).isEqualTo(NY)

        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).isEmpty()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Lang sykmelding som treffer over julesøknad perioden får først aktiver julesøknaden når foranliggende er aktivert`() {
        val orgnummer = "999999999"
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 10, 1),
            tom = LocalDate.of(nesteÅr, 12, 30),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )

        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).hasSize(1)

        var soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader).hasSize(3)
        assertThat(soknader[0].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[1].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        julesoknadService.prosseserJulesoknadKandidater()

        soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader[0].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[1].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        aktiverService.aktiverSoknader(soknader[2].fom!!)

        soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        julesoknadService.prosseserJulesoknadKandidater()

        soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(NY)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 6)
    }

    @Test
    fun `Exception kastes i prosseserJulesoknadKandidater() - aktiverSoknad() - soknadEvent()`() {

        val orgnummer = "111111111"
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer)
        val orgnummer2 = "999999999"
        mockArbeidsgiverForskutterer(Forskuttering.JA, orgnummer2)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 11, 1),
            tom = LocalDate.of(nesteÅr + 1, 1, 4),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Falafel")
        )

        var soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader).hasSize(3)
        assertThat(soknader[0].status).isEqualTo(FREMTIDIG) // Denne er egentlig allerede sendt
        assertThat(soknader[1].status).isEqualTo(FREMTIDIG) // Dette er kandidaten
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        aktiverService.aktiverSoknader(soknader[0].tom!!.plusDays(1))
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val aktiverteSoknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(aktiverteSoknader).hasSize(3)
        assertThat(aktiverteSoknader[0].status).isEqualTo(NY)
        assertThat(aktiverteSoknader[1].status).isEqualTo(FREMTIDIG)
        assertThat(aktiverteSoknader[2].status).isEqualTo(FREMTIDIG)

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = aktiverteSoknader[0], mockMvc = this, fnr = fnr)
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
        assertThat(sendtSoknad.status).isEqualTo(SENDT)
        flexSyketilfelleMockRestServiceServer?.reset()
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer2, orgNavn = "Kebab")
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
        // Vi endrer nærmestelederskjema og kjører cronjobben
        narmestelederMockRestServiceServer?.reset()
        evictAllCaches()
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer)
        mockArbeidsgiverForskutterer(Forskuttering.NEI, orgnummer2)

        doThrow(RuntimeException("Noe feiler"))
            .whenever(aivenKafkaProducer)
            .produserMelding(
                argWhere { it.id == soknader[1].id },
            )

        evictAllCaches()
        julesoknadService.prosseserJulesoknadKandidater() // Den ene skal feile stille og rulles tilbake, den andre skal gå gjennom

        soknader = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(soknader.size).isEqualTo(4)
        assertThat(soknader.filter { it.status == NY }.size).isEqualTo(1)
        assertThat(soknader.filter { it.status == FREMTIDIG }.size).isEqualTo(2)
        assertThat(soknader.filter { it.status == SENDT }.size).isEqualTo(1)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 7, duration = Duration.ofSeconds(2))
    }

    private fun sendInnSykmelding(
        fom: LocalDate,
        tom: LocalDate,
        statusEvent: String = STATUS_BEKREFTET,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
        arbeidsgiver: ArbeidsgiverStatusDTO? = null,
        fodselsnummer: String = fnr
    ) {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fodselsnummer,
            arbeidssituasjon = arbeidssituasjon,
            statusEvent = statusEvent,
            arbeidsgiver = arbeidsgiver
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = fom,
            tom = tom,
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)
    }
}
