package no.nav.helse.flex.helsearbeidsgiver

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.HentSoknaderRequest
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class InntektsmeldingApiTest : FellesTestOppsett() {
    private val fnr = "12345678900"
    private val fnr2 = "11111111111"
    private val basisdato = LocalDate.of(2021, 9, 1)

    @Autowired
    lateinit var vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository

    @Autowired
    lateinit var vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository

    @Test
    @Order(1)
    fun `Vi sender inn en del sykmeldinger`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.now(),
                orgnummer = "1234",
            ),
        ) shouldHaveSize 0
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(45),
                        tom = basisdato,
                    ),
            ),
            forventaSoknader = 2,
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(90),
                        tom = basisdato.minusDays(46),
                    ),
            ),
            forventaSoknader = 2,
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr2,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(90),
                        tom = basisdato.minusDays(46),
                    ),
            ),
            forventaSoknader = 2,
        )

        hentSoknaderMetadata(fnr) shouldHaveSize 4
        hentSoknaderMetadata(fnr2) shouldHaveSize 2
    }

    @Test
    @Order(2)
    fun `Vi finner 4 søknader med riktig orgnummer og eldsteFom langt tilbake`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = basisdato.minusDays(90),
                orgnummer = "123454543",
            ),
        ) shouldHaveSize 4
    }

    @Test
    @Order(2)
    fun `Vi finner 3 søknader med riktig orgnummer og eldsteFom dagen etter første sykmelding fom`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = basisdato.minusDays(89),
                orgnummer = "123454543",
            ),
        ) shouldHaveSize 3
    }

    @Test
    @Order(2)
    fun `Vi finner 0 søknader med feil orgnummer og eldsteFom dagen etter første sykmelding fom`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = basisdato.minusDays(89),
                orgnummer = "feil-org",
            ),
        ) shouldHaveSize 0
    }

    @Test
    @Order(2)
    fun `Vi finner 2 søknader med riktig orgnummer for det andre fødselsnummeret`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr2,
                eldsteFom = basisdato.minusDays(360),
                orgnummer = "123454543",
            ),
        ) shouldHaveSize 2
    }

    @Test
    @Order(3)
    fun `Vi returnerer vedtaksperiode id på en søknad hvis vi kjenner den`() {
        val soknader =
            hentSomArbeidsgiver(
                HentSoknaderRequest(
                    fnr = fnr2,
                    eldsteFom = basisdato.minusDays(360),
                    orgnummer = "123454543",
                ),
            )
        soknader[0].vedtaksperiodeId.shouldBeNull()
        soknader[1].vedtaksperiodeId.shouldBeNull()
        val tidspunkt = OffsetDateTime.now()

        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = UUID.randomUUID().toString(),
                behandlingId = UUID.randomUUID().toString(),
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = tidspunkt,
                eksterneSøknadIder = listOf(soknader.first().sykepengesoknadUuid),
            )

        sendBehandlingsstatusMelding(behandlingstatusmelding)
        await().atMost(5, TimeUnit.SECONDS).until {
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(
                listOf(soknader.first().sykepengesoknadUuid),
            ).firstOrNull()?.vedtaksperiodeBehandlingId != null
        }

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr2,
                eldsteFom = basisdato.minusDays(360),
                orgnummer = "123454543",
            ),
        ).let {
            it[0].vedtaksperiodeId.shouldNotBeNull()
            it[0].vedtaksperiodeId shouldBeEqualTo behandlingstatusmelding.vedtaksperiodeId
            it[1].vedtaksperiodeId.shouldBeNull()
        }
    }
}
