package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import io.getunleash.FakeUnleash
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.fakes.FlexSyketilfelleClientFake
import no.nav.helse.flex.fakes.FlexSykmeldingerBackendClientFake
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.NaringsdrivendeSoknadService
import no.nav.helse.flex.soknadsopprettelse.hentArbeidssituasjon
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class NaringsdrivendeSoknadServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var flexSyketilfelleClient: FlexSyketilfelleClientFake

    @Autowired
    lateinit var flexSykmeldingerBackendClient: FlexSykmeldingerBackendClientFake

    @Autowired
    lateinit var naringsdrivendeSoknadService: NaringsdrivendeSoknadService

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
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
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
    fun `Burde returnere ingen andre sykmeldinger med samme arbeidsforhold når opprettelsestoggle er av`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
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
                it.size `should be equal to` 0
            }
    }

    @Test
    fun `Burde ikke feile hardt når toggle er av`() {
        fakeUnleash.disable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val sykmelding = sykmeldingKafkaMessage(fnr = fnrSomFeiler, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
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
                identer = FolkeregisterIdenter(originalIdent = fnrSomFeiler, andreIdenter = emptyList()),
            ).also {
                it.size `should be equal to` 0
            }
    }

    @Test
    fun `Burde feile hardt når toggle er på`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)
        val sykmelding = sykmeldingKafkaMessage(fnr = fnrSomFeiler, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)

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
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETT_VENTETIDSOKNADER)

        val sykmelding = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE, status = STATUS_BEKREFTET)
        val sykmelding1 = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE, status = STATUS_BEKREFTET)
        val sykmelding2 = sykmeldingKafkaMessage(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE, status = STATUS_APEN)

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
}
