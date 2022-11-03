package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var prosesserJulesoknadkandidater: ProsesserJulesoknadkandidater

    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    @Autowired
    private lateinit var forskutteringRepository: ForskutteringRepository

    private final val fnr = "123456789"

    private final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        forskutteringRepository.deleteAll()
        flexSyketilfelleMockRestServiceServer?.reset()
        databaseReset.resetDatabase()
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode aktiveres når cron job kjøres`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15)
        )

        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode med tom ut i januar aktiveres når cron job kjøres`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 5),
            tom = LocalDate.of(nesteÅr + 1, 1, 1)
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
    }

    @Test
    fun `14 dagers arbeidsledig søknad i riktig periode aktiveres ikke`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 14)
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
    }

    @Test
    fun `15 dagers arbeidsledig søknad i med for sen fom aktiveres ikke`() {
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 8),
            tom = LocalDate.of(nesteÅr, 12, 22)
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode uten forskuttering aktiveres når cron job kjøres`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med ukjent forskuttering aktiveres når cron job kjøres`() {
        val orgnummer = "999999999"
        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(NY)
        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).isEmpty()
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med forskuttering aktiveres ikke når cron job kjøres`() {
        val orgnummer = "999999999"
        lagreForskuttering(true, orgnummer)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 12, 1),
            tom = LocalDate.of(nesteÅr, 12, 15),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab")
        )

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        val kandidater = julesoknadkandidatDAO.hentJulesoknadkandidater()
        assertThat(kandidater).hasSize(1)
        assertThat(kandidater.first().sykepengesoknadUuid).isEqualTo(soknaden.id)

        // Vi endrer nærmestelederskjema og kjører cronjobben
        forskutteringRepository.deleteAll()

        lagreForskuttering(false, orgnummer)

        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val soknaderEtterCronjob = hentSoknaderMetadata(fnr)
        assertThat(soknaderEtterCronjob).hasSize(1)

        val soknadenEtterCronjob = soknaderEtterCronjob.first()
        assertThat(soknadenEtterCronjob.status).isEqualTo(NY)

        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).isEmpty()
    }

    @Test
    fun `Lang sykmelding som treffer over julesøknad perioden får først aktiver julesøknaden når foranliggende er aktivert`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendInnSykmelding(
            fom = LocalDate.of(nesteÅr, 10, 1),
            tom = LocalDate.of(nesteÅr, 12, 30),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
            antallForventetSoknad = 3,
        )
        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()

        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).hasSize(1)

        var soknader = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(soknader).hasSize(3)
        assertThat(soknader[0].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[1].status).isEqualTo(FREMTIDIG)
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        aktiveringJob.bestillAktivering(soknader[2].fom!!)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        soknader = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(FREMTIDIG)

        prosesserJulesoknadkandidater.prosseserJulesoknadKandidater()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        soknader = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(soknader[0].status).isEqualTo(NY)
        assertThat(soknader[1].status).isEqualTo(NY)
        assertThat(soknader[2].status).isEqualTo(NY)
    }

    private fun lagreForskuttering(forskutterer: Boolean, orgnummer: String) {

        forskutteringRepository.save(
            Forskuttering(
                id = null,
                narmesteLederId = UUID.randomUUID(),
                brukerFnr = fnr,
                orgnummer = orgnummer,
                aktivFom = LocalDate.now(),
                aktivTom = null,
                arbeidsgiverForskutterer = forskutterer,
                timestamp = Instant.now(),
                oppdatert = Instant.now()
            )
        )
    }

    private fun sendInnSykmelding(
        fom: LocalDate,
        tom: LocalDate,
        statusEvent: String = STATUS_BEKREFTET,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
        arbeidsgiver: ArbeidsgiverStatusDTO? = null,
        fodselsnummer: String = fnr,
        antallForventetSoknad: Int = 1
    ) {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fodselsnummer,
            arbeidssituasjon = arbeidssituasjon,
            statusEvent = statusEvent,
            arbeidsgiver = arbeidsgiver
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = skapArbeidsgiverSykmelding(
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
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = antallForventetSoknad)
    }
}
