package no.nav.helse.flex.helsearbeidsgiver

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.HentSoknaderRequest
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.gradertSykmeldt
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flattenSporsmal
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class InntektsmeldingApiTest : FellesTestOppsett() {
    private val fnr = "11111111111"
    private val orgnummer = "999999999"

    @Autowired
    lateinit var vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var svarDAO: SvarDAO

    @BeforeEach
    fun database() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Finner sendt søknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 14),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 1,
        )

        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 1, 1),
                orgnummer = orgnummer,
            ),
        ) shouldHaveSize 1
    }

    @Test
    fun `Finner ikke søknad som ikke er sendt`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 14),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 1,
        )

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 1, 1),
                orgnummer = orgnummer,
            ),
        ) shouldHaveSize 0
    }

    @Test
    fun `Hent søknad med type BEHANDLINGSDAGER og riktig behandlingsdager`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    behandingsdager(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 21),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 1,
        )

        val sporsmal =
            sykepengesoknadDAO
                .finnSykepengesoknader(listOf(fnr))
                .single()
                .sporsmal
                .flattenSporsmal()

        listOf(
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_0" to "2025-09-01",
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_1" to "2025-09-08",
            "ENKELTSTAENDE_BEHANDLINGSDAGER_UKE_2" to "Ikke til behandling",
        ).forEach { (tag, verdi) ->
            sporsmal.find { it.tag == tag }!!.id?.let {
                svarDAO.lagreSvar(it, Svar(id = null, verdi = verdi))
            }
        }
        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2021, 1, 1),
                orgnummer = orgnummer,
            ),
        ).also {
            it.single().also { soknad ->
                soknad.soknadstype `should be equal to` Soknadstype.BEHANDLINGSDAGER
                soknad.behandlingsdager shouldContainAll
                    listOf(
                        LocalDate.parse("2025-09-01"),
                        LocalDate.parse("2025-09-08"),
                    )
            }
        }
    }

    @Test
    fun `Filtrer søknader basert på eldsteFom`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 10, 31),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 2,
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 11, 1),
                        tom = LocalDate.of(2025, 12, 31),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 2,
        )
        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 1, 1),
                orgnummer = orgnummer,
            ),
        ) shouldHaveSize 4

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 9, 8),
                orgnummer = orgnummer,
            ),
        ) shouldHaveSize 3
    }

    @Test
    fun `Finner ikke søknad med feil orgnummer`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 7),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 1,
        )
        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 1, 1),
                orgnummer = orgnummer,
            ),
        ) shouldHaveSize 1

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                LocalDate.of(2025, 1, 1),
                orgnummer = "ukjent-org",
            ),
        ) shouldHaveSize 0
    }

    @Test
    fun `Returnerer søknad med mottatt vedtaksperiodeId`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 10, 31),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
            ),
            forventaSoknader = 2,
        )
        markerSoknaderSomSendt()

        val soknader =
            hentSomArbeidsgiver(
                HentSoknaderRequest(
                    fnr = fnr,
                    eldsteFom = LocalDate.of(2025, 1, 1),
                    orgnummer = orgnummer,
                ),
            )
        soknader[0].vedtaksperiodeId.shouldBeNull()
        soknader[1].vedtaksperiodeId.shouldBeNull()

        val behandlingstatusmelding =
            Behandlingstatusmelding(
                vedtaksperiodeId = UUID.randomUUID().toString(),
                behandlingId = UUID.randomUUID().toString(),
                status = Behandlingstatustype.OPPRETTET,
                tidspunkt = OffsetDateTime.now(),
                eksterneSøknadIder = listOf(soknader.first().sykepengesoknadUuid),
            )
        sendBehandlingsstatusMelding(behandlingstatusmelding)

        await().atMost(1, TimeUnit.SECONDS).until {
            vedtaksperiodeBehandlingSykepengesoknadRepository
                .findBySykepengesoknadUuidIn(
                    listOf(soknader.first().sykepengesoknadUuid),
                ).firstOrNull()
                ?.vedtaksperiodeBehandlingId != null
        }

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                eldsteFom = LocalDate.of(2025, 1, 1),
                orgnummer = orgnummer,
            ),
        ).also {
            it.first { s -> s.sykepengesoknadUuid == soknader[0].sykepengesoknadUuid }.vedtaksperiodeId shouldBeEqualTo
                behandlingstatusmelding.vedtaksperiodeId
            it.first { s -> s.sykepengesoknadUuid == soknader[1].sykepengesoknadUuid }.vedtaksperiodeId.shouldBeNull()
        }
    }

    @Test
    fun `Hent søknad med riktig kalkulert grad og faktiskGrad når bruker har jobbet i begge perioder`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 7),
                        grad = 50,
                    ) +
                        heltSykmeldt(
                            fom = LocalDate.of(2025, 9, 8),
                            tom = LocalDate.of(2025, 9, 14),
                        ),
            ),
            forventaSoknader = 1,
        )

        val soknad = hentSoknader(fnr).single()

        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "JOBBET_DU_GRADERT_0", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MANGE_TIMER_PER_UKE_0", svar = "40", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_0", svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_VERDI_0", svar = "30")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_1", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "JOBBER_DU_NORMAL_ARBEIDSUKE_1", svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MANGE_TIMER_PER_UKE_1", svar = "40", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_1", svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_VERDI_1", svar = "10")

        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                orgnummer = orgnummer,
                eldsteFom = LocalDate.of(2025, 1, 1),
            ),
        ).single().also {
            it.soknadsperioder shouldHaveSize 2

            it.soknadsperioder[0].also { periode ->
                periode.grad shouldBeEqualTo 50
                periode.faktiskGrad shouldBeEqualTo 75
            }

            it.soknadsperioder[1].also { periode ->
                periode.grad shouldBeEqualTo 100
                periode.faktiskGrad shouldBeEqualTo 25
            }
        }
    }

    @Test
    fun `Hent søknad med riktig kalkulert grad og faktiskGrad når bruker har jobbet i én periode`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = orgnummer, orgNavn = "Arbeidsgiver"),
                sykmeldingsperioder =
                    gradertSykmeldt(
                        fom = LocalDate.of(2025, 9, 1),
                        tom = LocalDate.of(2025, 9, 7),
                        grad = 50,
                    ) +
                        heltSykmeldt(
                            fom = LocalDate.of(2025, 9, 8),
                            tom = LocalDate.of(2025, 9, 14),
                        ),
            ),
            forventaSoknader = 1,
        )

        val soknad = hentSoknader(fnr).single()

        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "JOBBET_DU_GRADERT_0", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MANGE_TIMER_PER_UKE_0", svar = "40", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_0", svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(tag = "HVOR_MYE_TIMER_VERDI_0", svar = "30")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_1", svar = "NEI", ferdigBesvart = false)

        markerSoknaderSomSendt()

        hentSomArbeidsgiver(
            HentSoknaderRequest(
                fnr = fnr,
                orgnummer = orgnummer,
                eldsteFom = LocalDate.of(2025, 1, 1),
            ),
        ).single().also {
            it.soknadsperioder shouldHaveSize 2

            it.soknadsperioder[0].also { periode ->
                periode.grad shouldBeEqualTo 50
                periode.faktiskGrad shouldBeEqualTo 75
            }

            it.soknadsperioder[1].also { periode ->
                periode.grad shouldBeEqualTo 100
                periode.faktiskGrad shouldBe null
            }
        }
    }

    private fun markerSoknaderSomSendt() {
        sykepengesoknadRepository.findAll().toList().forEach {
            sykepengesoknadRepository.save(it.copy(status = Soknadstatus.SENDT))
        }
    }
}
