package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.FellesTestOppsett
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
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadIntegrationTest : FellesTestOppsett() {
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
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == NY) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBeEqualTo true
    }

    @Test
    fun `15 dagers arbeidsledig søknad i riktig periode med tom ut i januar aktiveres når cron job kjøres`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 5),
                        tom = LocalDate.of(nesteÅr + 1, 1, 1),
                    ),
            ),
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == NY) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBeEqualTo true
    }

    @Test
    fun `14 dagers arbeidsledigsøknad er ikke julesoknadkandidat og aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 14),
                    ),
            ),
        )

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == FREMTIDIG) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe null
    }

    @Test
    fun `15 dagers arbeidsledigsøknad i med for sen FOM aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 8),
                        tom = LocalDate.of(nesteÅr, 12, 22),
                    ),
            ),
        )

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == FREMTIDIG) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe null
    }

    @Test
    fun `15 dagers arbeidstakersøknad i riktig periode uten forskuttering aktiveres`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == NY) },
            )

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBeEqualTo true
    }

    @Test
    fun `15 dagers arbeidstakersøknad i riktig periode hvor forskuttering fjernes forskuttering aktiveres`() {
        val orgnummer = "999999999"
        lagreForskuttering(true, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.status).isEqualTo(FREMTIDIG)

        julesoknadkandidatDAO.hentJulesoknadkandidater().run {
            this shouldHaveSize 1
            this.first().sykepengesoknadUuid shouldBeEqualTo soknaden.id
        }

        // Lagre at arbeidsgiver ikke forskutterer.
        forskutteringRepository.deleteAll()
        lagreForskuttering(false, orgnummer)

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val prosesserteSoknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == NY) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(prosesserteSoknader.first().id) shouldBeEqualTo true
    }

    @Test
    fun `15 dagers arbeidstakersøknad i riktig periode med ukjent forskuttering aktiveres`() {
        val orgnummer = "999999999"

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == NY) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBeEqualTo true
    }

    @Test
    fun `15 dagers arbeidstakersøknad i riktig periode med forskuttering aktiveres ikke`() {
        val orgnummer = "999999999"
        lagreForskuttering(true, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr) },
                { (it.size == 1 && it.first().status == FREMTIDIG) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe null
    }

    @Test
    fun `Lang sykmelding som treffer over julesøknadperioden får aktivert julesøknaden når foranliggende er aktivert`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 10, 1),
                        tom = LocalDate.of(nesteÅr, 12, 30),
                    ),
            ),
            forventaSoknader = 3,
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknaderEtterForsteProsessering = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        await().atMost(Duration.ofSeconds(5)).until(
            { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
            { (it.size == 3 && it.all { it.status == FREMTIDIG }) },
        )

        aktiveringJob.bestillAktivering(soknaderEtterForsteProsessering[2].fom!!)
        await().atMost(Duration.ofSeconds(5)).until(
            { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
            {
                (
                    it.size == 3 &&
                        it[0].status == NY &&
                        it[1].status == NY &&
                        it[2].status == FREMTIDIG
                )
            },
        )

        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
                { (it.size == 3 && it.all { it.status == NY }) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.last().id) shouldBeEqualTo true
    }

    @Test
    fun `Aktiverer julesøknad når tidligere søknad fra annen sykmelding blir aktivert`() {
        val orgnummer = "999999999"
        lagreForskuttering(false, orgnummer)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 12, 1),
                        tom = LocalDate.of(nesteÅr, 12, 15),
                    ),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(nesteÅr, 11, 15),
                        tom = LocalDate.of(nesteÅr, 11, 30),
                    ),
            ),
        )
        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()
        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1

        val soknaderEtterForsteProsessering =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
                { (it.size == 2 && it.all { it.status == FREMTIDIG }) },
            )

        aktiveringJob.bestillAktivering(soknaderEtterForsteProsessering[1].fom!!)

        await().atMost(Duration.ofSeconds(5)).until(
            { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
            {
                (
                    it.size == 2 &&
                        it[0].status == NY &&
                        it[1].status == FREMTIDIG
                )
            },
        )

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        val soknader =
            await().atMost(Duration.ofSeconds(5)).until(
                { hentSoknaderMetadata(fnr).sortedBy { it.fom } },
                { (it.size == 2 && it.all { it.status == NY }) },
            )

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.last().id) shouldBeEqualTo true
    }

    @Test
    fun `Fjerner julesøknadkandidat når søknaden er borte`() {
        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        julesoknadkandidatDAO.lagreJulesoknadkandidat(UUID.randomUUID().toString())
        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
    }

    private fun lagreForskuttering(
        forskutterer: Boolean,
        orgnummer: String,
    ) {
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
                oppdatert = Instant.now(),
            ),
        )
    }
}
