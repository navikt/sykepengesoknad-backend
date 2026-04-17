package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import io.getunleash.FakeUnleash
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.fakes.FlexSyketilfelleClientFake
import no.nav.helse.flex.fakes.FlexSykmeldingerBackendClientFake
import no.nav.helse.flex.fakes.SoknadLagrerFake
import no.nav.helse.flex.fakes.SykepengesoknadRepositoryFake
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.NaringsdrivendeSoknadService
import no.nav.helse.flex.soknadsopprettelse.VENTETIDSPERIODE
import no.nav.helse.flex.soknadsopprettelse.hentArbeidssituasjon
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.lagSoknad
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class NaringsdrivendeSoknadServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var flexSyketilfelleClient: FlexSyketilfelleClientFake

    @Autowired
    lateinit var flexSykmeldingerBackendClient: FlexSykmeldingerBackendClientFake

    @Autowired
    lateinit var naringsdrivendeSoknadService: NaringsdrivendeSoknadService

    @Autowired
    lateinit var soknadLagrer: SoknadLagrerFake

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepositoryFake

    @Autowired
    lateinit var fakeUnleash: FakeUnleash

    @AfterEach
    fun teardown() {
        fakeUnleash.resetAll()
        flexSyketilfelleClient.resetSykmeldingerMedSammeVentetid()
    }

    private val fnr = "fnr"
    private val fnrSomFeiler = "kast-feil"

    @Test
    fun `Burde kun finne sykmeldinger med samme arbeidsforhold`() {
        val sykmelding = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
        val sykmelding1 = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
        val sykmelding2 = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.FRILANSER)

        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding2.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding2)

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding,
                arbeidssituasjon = sykmelding.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 1
                it.single() `should be equal to` sykmelding1
            }
    }

    @Test
    fun `Burde feile hardt`() {
        val sykmelding =
            sykmeldingKafkaMessage(fnr = fnrSomFeiler, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)

        assertThrows<RuntimeException> {
            naringsdrivendeSoknadService
                .finnAndreSykmeldingerSomManglerSoknad(
                    sykmeldingKafkaMessage = sykmelding,
                    arbeidssituasjon = sykmelding.hentArbeidssituasjon()!!,
                    identer = FolkeregisterIdenter(originalIdent = fnrSomFeiler, andreIdenter = emptyList()),
                )
        }
    }

    @Test
    fun `Burde kun finne sykmeldinger som har status BEKREFTET (er sendt inn av bruker)`() {
        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
            )
        val sykmelding2 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_APEN,
            )

        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding2.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding2)

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding,
                arbeidssituasjon = sykmelding.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 1
                it.single() `should be equal to` sykmelding1
            }
    }

    @Test
    fun `Burde opprette ventetidsøknad hvis eksisterende søknad med samme ventetid er på eller etter sykmeldingen`() {
        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
            )
        val sykmelding2 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
            )

        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding2.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding2)

        soknadLagrer.lagreSoknad(
            opprettSoknad(
                fom = sykmelding.sykmelding.fom!!,
                tom = sykmelding.sykmelding.tom!!,
                sykmeldingId = sykmelding1.sykmelding.id,
            ),
        )

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding,
                arbeidssituasjon = sykmelding.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 1
                it.first() `should be equal to` sykmelding2
            }
    }

    @Test
    fun `Burde opprette ventetidsøknad hvis det ikke er eksisterende søknad og er innenfor ventetidsperioden`() {
        val fom = LocalDate.of(2020, 2, 21)

        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = fom.minusDays(VENTETIDSPERIODE.toLong()),
                    ),
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = fom,
                        tom = LocalDate.of(2020, 2, 24),
                    ),
            )
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding1,
                arbeidssituasjon = sykmelding1.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding1.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 1
                it.first() `should be equal to` sykmelding
            }
    }

    @Test
    fun `Burde ikke opprette ventetidsøknad hvis det ikke er eksisterende søknad men er utenfor ventetidsperioden`() {
        val fom = LocalDate.of(2020, 2, 21)

        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = fom.minusDays(VENTETIDSPERIODE.toLong() + 1),
                    ),
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = fom,
                        tom = LocalDate.of(2020, 2, 24),
                    ),
            )
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding1,
                arbeidssituasjon = sykmelding1.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding1.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 0
            }
    }

    @Test
    fun `Burde ikke opprette ventetidsøknader hvis eksisterende søknad med samme ventetid er før sykmeldingen`() {
        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 2, 5),
                    ),
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 6),
                        tom = LocalDate.of(2020, 2, 11),
                    ),
            )
        val sykmelding2 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 12),
                        tom = LocalDate.of(2020, 2, 19),
                    ),
            )
        val sykmelding3 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 20),
                        tom = LocalDate.of(2020, 2, 25),
                    ),
            )

        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding2.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding3.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding2)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding3)

        soknadLagrer.lagreSoknad(
            opprettSoknad(
                fom = sykmelding1.sykmelding.fom!!,
                tom = sykmelding1.sykmelding.tom!!,
                sykmeldingId = sykmelding1.sykmelding.id,
            ),
        )

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding2,
                arbeidssituasjon = sykmelding2.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding2.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.first() `should be equal to` sykmelding3
            }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `Burde opprette ventetidsøknader hvis eksisterende søknad før sykmeldingen er annen arbeidssituasjon og innenfor ventetidsperioden`() {
        val sykmelding =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 2, 5),
                    ),
            )
        val sykmelding1 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 6),
                        tom = LocalDate.of(2020, 2, 11),
                    ),
            )
        val sykmelding2 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 12),
                        tom = LocalDate.of(2020, 2, 19),
                    ),
            )
        val sykmelding3 =
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                status = STATUS_BEKREFTET,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 20),
                        tom = LocalDate.of(2020, 2, 25),
                    ),
            )

        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding1.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding2.sykmelding.id)
        flexSyketilfelleClient.leggTilSykmeldingMedSammeVentetid(sykmelding3.sykmelding.id)

        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding1)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding2)
        flexSykmeldingerBackendClient.leggTilSykmelding(sykmelding3)

        soknadLagrer.lagreSoknad(
            opprettSoknad(
                fom = sykmelding1.sykmelding.fom!!,
                tom = sykmelding1.sykmelding.tom!!,
                sykmeldingId = sykmelding1.sykmelding.id,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            ),
        )

        naringsdrivendeSoknadService
            .finnAndreSykmeldingerSomManglerSoknad(
                sykmeldingKafkaMessage = sykmelding2,
                arbeidssituasjon = sykmelding2.hentArbeidssituasjon()!!,
                identer =
                    FolkeregisterIdenter(
                        originalIdent = sykmelding2.kafkaMetadata.fnr,
                        andreIdenter = emptyList(),
                    ),
            ).also {
                it.size `should be equal to` 2
                it.find { sykmelding -> sykmelding.sykmelding.id == sykmelding.sykmelding.id } `should be equal to` sykmelding
                it.find { sykmelding -> sykmelding.sykmelding.id == sykmelding3.sykmelding.id } `should be equal to` sykmelding3
            }
    }

    private fun opprettSoknad(
        fom: LocalDate,
        tom: LocalDate,
        sykmeldingId: String,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
        soknadstype: Soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
    ): Sykepengesoknad =
        lagSoknad(
            fnr = fnr,
            arbeidsgiver = 1,
            fom = fom,
            tom = tom,
            startSykeforlop = LocalDate.of(2023, 1, 1),
            arbeidsSituasjon = arbeidssituasjon,
            soknadsType = soknadstype,
            status = Soknadstatus.NY,
            sykmeldingId = sykmeldingId,
        )
}
