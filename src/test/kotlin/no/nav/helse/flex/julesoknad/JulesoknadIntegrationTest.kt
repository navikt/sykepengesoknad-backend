package no.nav.helse.flex.julesoknad

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.FREMTIDIG
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus.NY
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Duration
import java.time.LocalDate
import java.util.*

const val ORG_NUMMER = "999999999"

@TestMethodOrder(MethodOrderer.MethodName::class)
class JulesoknadIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var prosesserJulesoknadkandidat: JulesoknadCronJob

    @Autowired
    private lateinit var julesoknadkandidatDAO: JulesoknadkandidatDAO

    private final val fnr = "123456789"

    private final val nesteÅr = LocalDate.now().plusYears(1).year

    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        databaseReset.resetDatabase()
    }

    @Test
    fun `15 dager lang arbeidsledigsøknad i riktig periode aktiveres når cron job kjøres`() {
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
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe true
        sykepengesoknadKafkaConsumer.ventPåRecords(1)
    }

    @Test
    fun `15 dager lang arbeidsledigsøknad i riktig periode med tom i neste år aktiveres når cron job kjøres`() {
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
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe true
        sykepengesoknadKafkaConsumer.ventPåRecords(1)
    }

    @Test
    fun `14 dager lang arbeidsledigsøknad er ikke julesoknadkandidat og aktiveres ikke`() {
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

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe false
    }

    @Test
    fun `14 dager lang arbeidstakersøknad er ikke julesoknadkandidat og aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
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

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe false
    }

    @Test
    fun `15 dager lang arbeidsledigsøknad i med for sen fom aktiveres ikke`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        // 7. desember er siste gyldige tom.
                        fom = LocalDate.of(nesteÅr, 12, 8),
                        tom = LocalDate.of(nesteÅr, 12, 31),
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

        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe false
    }

    @Test
    fun `15 dager lang arbeidstakersøknad i riktig periode tidligaktiveres ikke uten forskutteringsdata`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = ORG_NUMMER, orgNavn = "Arbeidsgiver"),
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
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe false

        aktiveringJob.bestillAktivering(LocalDate.of(nesteÅr, 12, 16))
        await().atMost(Duration.ofSeconds(5)).until(
            { hentSoknaderMetadata(fnr).single().status },
            { it == NY },
        )
        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.first().id) shouldBe false
        sykepengesoknadKafkaConsumer.ventPåRecords(1)
    }

    @Test
    fun `Lang sykmelding for arbeidsledig får aktivert julesøknaden når foranliggende er aktivert`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
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
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.last().id) shouldBe true
        sykepengesoknadKafkaConsumer.ventPåRecords(3)
    }

    @Test
    fun `Aktiverer julesøknad for arbeidsledig når tidligere søknad fra annen sykmelding blir aktivert`() {
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
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
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
        sykepengesoknadRepository.erAktivertJulesoknadKandidat(soknader.last().id) shouldBe true
        sykepengesoknadKafkaConsumer.ventPåRecords(2)
    }

    @Test
    fun `Fjerner julesøknadkandidat når søknaden er borte`() {
        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
        julesoknadkandidatDAO.lagreJulesoknadkandidat(UUID.randomUUID().toString())
        julesoknadkandidatDAO.hentJulesoknadkandidater() shouldHaveSize 1

        prosesserJulesoknadkandidat.prosseserJulesoknadKandidater()

        julesoknadkandidatDAO.hentJulesoknadkandidater().shouldBeEmpty()
    }
}
