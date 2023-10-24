package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.FREMTIDIG
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.NY
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.amshove.kluent.shouldBeEmpty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var prosesserJulesoknadkandidat: JulesoknadCronJob

    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    @Autowired
    private lateinit var forskutteringRepository: ForskutteringRepository

    private final val fnr = "123456789"

    private final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        forskutteringRepository.deleteAll()
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()
    }

    @BeforeEach
    fun `Sjekk at topic er tomt`() {
        sykepengesoknadKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterEach
    fun rydd() {
        sykepengesoknadKafkaConsumer.hentProduserteRecords()
        sykepengesoknadKafkaConsumer.hentProduserteRecords()
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode aktiveres når cron job kjøres`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 15)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(NY)
        }
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode med tom ut i januar aktiveres når cron job kjøres`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 5),
                    tom = LocalDate.of(nesteÅr + 1, 1, 1)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(NY)
        }
    }

    @Test
    fun `14 dagers arbeidsledig søknad i riktig periode aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 14)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        }
    }

    @Test
    fun `15 dagers arbeidsledig søknad i med for sen fom aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 8),
                    tom = LocalDate.of(nesteÅr, 12, 22)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(FREMTIDIG)
        }
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode uten forskuttering aktiveres når cron job kjøres`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 15)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(NY)
        }
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med ukjent forskuttering aktiveres når cron job kjøres`() {
        val orgnummer = "999999999"

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 15)
                )
            )
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknader = hentSoknaderMetadata(fnr)
            assertThat(soknader).hasSize(1)
            val soknaden = soknader.first()
            assertThat(soknaden.status).isEqualTo(NY)
            assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).isEmpty()
        }
    }

    @Test
    fun `15 dagers arbeidstaker søknad i riktig periode med forskuttering aktiveres ikke når cron job kjøres`() {
        val orgnummer = "999999999"
        lagreForskuttering(true, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 15)
                )
            )
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

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
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

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 10, 1),
                    tom = LocalDate.of(nesteÅr, 12, 30)
                )
            ),
            forventaSoknader = 3
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).hasSize(1)

        val soknaderEtterForsteProsessering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(soknaderEtterForsteProsessering).hasSize(3)
            assertThat(soknaderEtterForsteProsessering[0].status).isEqualTo(FREMTIDIG)
            assertThat(soknaderEtterForsteProsessering[1].status).isEqualTo(FREMTIDIG)
            assertThat(soknaderEtterForsteProsessering[2].status).isEqualTo(FREMTIDIG)
        }

        aktiveringJob.bestillAktivering(soknaderEtterForsteProsessering[2].fom!!)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknaderEtterAktivering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            assertThat(soknaderEtterAktivering[0].status).isEqualTo(NY)
            assertThat(soknaderEtterAktivering[1].status).isEqualTo(NY)
            assertThat(soknaderEtterAktivering[2].status).isEqualTo(FREMTIDIG)
        }

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknaderEtterAndreProsessering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            assertThat(soknaderEtterAndreProsessering[0].status).isEqualTo(NY)
            assertThat(soknaderEtterAndreProsessering[1].status).isEqualTo(NY)
            assertThat(soknaderEtterAndreProsessering[2].status).isEqualTo(NY)
        }
    }

    @Test
    fun `Lang søknad som treffer over julesøknad perioden får først aktivert julesøknaden når foranliggende søknad til annen sykmelding er aktivert`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 12, 1),
                    tom = LocalDate.of(nesteÅr, 12, 15)
                )
            )
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = orgnummer, orgNavn = "Kebab"),
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(nesteÅr, 11, 15),
                    tom = LocalDate.of(nesteÅr, 11, 30)
                )
            )
        )
        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).hasSize(1)
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        assertThat(julesoknadkandidatDAO.hentJulesoknadkandidater()).hasSize(1)

        val soknaderEtterForsteProsessering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(soknaderEtterForsteProsessering).hasSize(2)
            assertThat(soknaderEtterForsteProsessering[0].status).isEqualTo(FREMTIDIG)
            assertThat(soknaderEtterForsteProsessering[1].status).isEqualTo(FREMTIDIG)
        }

        aktiveringJob.bestillAktivering(soknaderEtterForsteProsessering[1].fom!!)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknaderEtterAktivering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            assertThat(soknaderEtterAktivering[0].status).isEqualTo(NY)
            assertThat(soknaderEtterAktivering[1].status).isEqualTo(FREMTIDIG)
        }

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknaderEtterAndreProsessering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            assertThat(soknaderEtterAndreProsessering[0].status).isEqualTo(NY)
            assertThat(soknaderEtterAndreProsessering[1].status).isEqualTo(NY)
        }
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
}
