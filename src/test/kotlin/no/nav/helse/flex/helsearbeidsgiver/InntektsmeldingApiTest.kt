package no.nav.helse.flex.helsearbeidsgiver

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.HentSoknaderRequest
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.flattenSporsmal
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
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
    private val fnr1 = "12345678900"
    private val fnr2 = "11111111111"
    private val fnr3 = "22222222222"

    private val basisdato = LocalDate.of(2021, 9, 1)

    @Autowired
    lateinit var vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var svarDAO: SvarDAO

    @Test
    @Order(1)
    fun `Det finnes ingen søknader`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = LocalDate.now(),
                orgnummer = "1234",
            ),
        ) shouldHaveSize 0
    }

    @Test
    @Order(2)
    fun `Send inn sykmeldinger`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = LocalDate.now(),
                orgnummer = "1234",
            ),
        ) shouldHaveSize 0

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr1,
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
                fnr = fnr1,
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

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr3,
                sykmeldingsperioder =
                    behandingsdager(
                        fom = basisdato.minusDays(25),
                        tom = basisdato,
                    ),
            ),
            forventaSoknader = 1,
        )

        hentSoknaderMetadata(fnr1) shouldHaveSize 4
        hentSoknaderMetadata(fnr2) shouldHaveSize 2
        hentSoknaderMetadata(fnr3) shouldHaveSize 1
    }

    @Test
    @Order(3)
    fun `Vi finner ingen søknader før de er sendt`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = basisdato.minusDays(90),
                orgnummer = "123454543",
            ),
        ) shouldHaveSize 0
    }

    @Test
    @Order(4)
    fun `Sett alle søknader til SENDT i databasen`() {
        sykepengesoknadRepository.findAll().toList().forEach {
            sykepengesoknadRepository.save(it.copy(status = Soknadstatus.SENDT))
        }
    }

    @Test
    @Order(5)
    fun `Vi finner 4 søknader med riktig orgnummer og eldsteFom langt tilbake`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = basisdato.minusDays(90),
                orgnummer = "123454543",
            ),
        ).also {
            it.size `should be equal to` 4
            it.first().soknadstype `should be equal to` Soknadstype.ARBEIDSTAKERE
            it.first().behandlingsdager.shouldBeEmpty()
        }
    }

    @Test
    @Order(5)
    fun `Vi finner 3 søknader med riktig orgnummer og eldsteFom dagen etter første sykmelding fom`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = basisdato.minusDays(89),
                orgnummer = "123454543",
            ),
        ).also {
            it.size `should be equal to` 3
            it.first().soknadstype `should be equal to` Soknadstype.ARBEIDSTAKERE
            it.first().behandlingsdager.shouldBeEmpty()
        }
    }

    @Test
    @Order(5)
    fun `Vi finner 0 søknader med feil orgnummer og eldsteFom dagen etter første sykmelding fom`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr1,
                eldsteFom = basisdato.minusDays(89),
                orgnummer = "feil-org",
            ),
        ) shouldHaveSize 0
    }

    @Test
    @Order(5)
    fun `Vi finner 2 søknader med riktig orgnummer for andre fødselsnummer`() {
        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr2,
                eldsteFom = basisdato.minusDays(360),
                orgnummer = "123454543",
            ),
        ) shouldHaveSize 2
    }

    @Test
    @Order(5)
    fun `Vi finner 1 søknader med type BEHANDLINGSDAGER for tredje fødselsnummer`() {
        val sporsmal =
            sykepengesoknadDAO
                .finnSykepengesoknader(listOf(fnr3))
                .single()
                .sporsmal
                .flattenSporsmal()

        // Lagrer svar på spørsmål om behandlingsdager sånn at vi kan testet at HentSoknaderResponse returnerer dagene.
        listOf(
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0" to "2025-01-01",
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1" to "2025-03-31",
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_2" to "Ikke til behandling",
        ).forEach { (tag, verdi) ->
            sporsmal.find { it.tag == tag }!!.id?.let {
                svarDAO.lagreSvar(it, Svar(id = null, verdi = verdi))
            }
        }

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr3,
                eldsteFom = basisdato.minusDays(360),
                orgnummer = "123454543",
            ),
        ).also {
            it.single().also { soknad ->
                soknad.soknadstype `should be equal to` Soknadstype.BEHANDLINGSDAGER
                soknad.behandlingsdager shouldContainAll
                    listOf(
                        LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2025-03-31"),
                    )
            }
        }
    }

    @Test
    @Order(6)
    fun `Vi returnerer vedtaksperiodeId til kjent søknad`() {
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
            vedtaksperiodeBehandlingSykepengesoknadRepository
                .findBySykepengesoknadUuidIn(
                    listOf(soknader.first().sykepengesoknadUuid),
                ).firstOrNull()
                ?.vedtaksperiodeBehandlingId != null
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
