package no.nav.helse.flex.papirsykmelding

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.*
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.util.*

/**
 * Veiledere endrer på feil i papir og utenlandsk sykmelding
 */
@TestMethodOrder(MethodOrderer.MethodName::class)
class AutomatiskPapirsykmeldingOpprydningTest : FellesTestOppsett() {
    @BeforeEach
    fun setUp() {
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(3)
    }

    private val fnr = "12345678900"

    private val sykmeldingId = UUID.randomUUID().toString()
    private val papirsykmeldingKafkaMessage =
        sykmeldingKafkaMessage(
            erPapirsykmelding = true,
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingId = sykmeldingId,
            sykmeldingsperioder =
                heltSykmeldt(
                    fom = LocalDate.of(2020, 1, 1),
                    tom = LocalDate.of(2020, 3, 15),
                ),
        )

    @Test
    fun `1 - Søknader til arbeidstaker opprettes for en lang papirsykmelding`() {
        mockFlexSyketilfelleSykeforloep(papirsykmeldingKafkaMessage.sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingKafkaMessage = papirsykmeldingKafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3).tilSoknader().let { sykepengesoknadDTOS ->
            sykepengesoknadDTOS.size `should be equal to` 3
            sykepengesoknadDTOS.all { it.status == NY } shouldBe true
        }

        hentSoknaderMetadata(fnr).size `should be equal to` 3
    }

    @Test
    fun `2 - vi sender inn den ene søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val førsteSøknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.fom == LocalDate.of(2020, 1, 1) }.id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = førsteSøknad, testOppsettInterfaces = this, fnr = fnr)
            .standardSvar()
            .sendSoknad()
            .let {
                it.status `should be equal to` RSSoknadstatus.SENDT
            }

        sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .tilSoknader()
            .shouldHaveSize(1)
            .single()
            .status
            .`should be equal to`(SENDT)
    }

    @Test
    fun `3 - vi mottar identisk sykmelding igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(papirsykmeldingKafkaMessage.sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, papirsykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        hentSoknaderMetadata(fnr).let {
            it.size `should be equal to` 3
            it.first().status `should be equal to` RSSoknadstatus.SENDT
            it.takeLast(2).all { soknad -> soknad.status == RSSoknadstatus.NY } shouldBe true
        }
    }

    @Test
    fun `4 - vi mottar en korrigert sykmelding med lengre periode, sendt blir korrigert og søknadene opprettes på nytt`() {
        val nyKafkaMessage =
            papirsykmeldingKafkaMessage.endrePeriode(
                periode =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 1, 1),
                        tom = LocalDate.of(2020, 4, 5),
                    ),
            )

        mockFlexSyketilfelleSykeforloep(papirsykmeldingKafkaMessage.sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingKafkaMessage = nyKafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 6).tilSoknader().let {
            it.take(2).all { soknad -> soknad.status == SLETTET } shouldBe true
            it.takeLast(4).all { soknad -> soknad.status == NY } shouldBe true
        }

        hentSoknaderMetadata(fnr).let {
            it.first().status `should be equal to` RSSoknadstatus.KORRIGERT
            it.takeLast(4).all { soknad -> soknad.status == RSSoknadstatus.NY } shouldBe true
        }
    }

    @Test
    fun `5 - vi mottar den korrigerte sykmeldingen igjen, ingenting endres`() {
        mockFlexSyketilfelleSykeforloep(papirsykmeldingKafkaMessage.sykmelding.id)

        val nyKafkaMessage =
            papirsykmeldingKafkaMessage.endrePeriode(
                periode =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 1, 1),
                        tom = LocalDate.of(2020, 4, 5),
                    ),
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingKafkaMessage = nyKafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        hentSoknaderMetadata(fnr).let {
            it.size `should be equal to` 5
            it.first().status `should be equal to` RSSoknadstatus.KORRIGERT
            it.takeLast(4).all { soknad -> soknad.status == RSSoknadstatus.NY } shouldBe true
        }
    }

    @Test
    fun `6 - sykmeldingen korrigeres igjen, men må med annen sykmeldingsgrad`() {
        mockFlexSyketilfelleSykeforloep(papirsykmeldingKafkaMessage.sykmelding.id)
        val nyKafkaMessage =
            papirsykmeldingKafkaMessage.endrePeriode(
                periode =
                    gradertSykmeldt(
                        fom = LocalDate.of(2020, 1, 1),
                        tom = LocalDate.of(2020, 4, 5),
                    ),
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingKafkaMessage = nyKafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 8).tilSoknader().let {
            it.size `should be equal to` 8
            it.take(4).all { soknad -> soknad.status == SLETTET } shouldBe true
            it.takeLast(4).all { soknad -> soknad.status == NY } shouldBe true
        }

        hentSoknaderMetadata(fnr).let {
            it.size `should be equal to` 5
            it.first().status `should be equal to` RSSoknadstatus.KORRIGERT
            it.takeLast(4).all { soknad -> soknad.status == RSSoknadstatus.NY } shouldBe true
        }
    }

    private fun SykmeldingKafkaMessageDTO.endrePeriode(periode: List<SykmeldingsperiodeAGDTO>) =
        this.copy(sykmelding = this.sykmelding.copy(sykmeldingsperioder = periode))
}
