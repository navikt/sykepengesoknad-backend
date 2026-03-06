package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.fakes.FlexSyketilfelleClientFake
import no.nav.helse.flex.fakes.FlexSykmeldingerBackendClientFake
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class NaringsdrivendeSoknadServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var flexSyketilfelleClient: FlexSyketilfelleClientFake

    @Autowired
    lateinit var flexSykmeldingerBackendClient: FlexSykmeldingerBackendClientFake

    @Autowired
    lateinit var naringsdrivendeSoknadService: NaringsdrivendeSoknadService

    @AfterEach
    fun setUp() {
        flexSyketilfelleClient.resetSykmeldingerMedSammeVentetid()
    }

    @Test
    fun `Burde kun finne sykmeldinger med samme arbeidsforhold`() {
        val sykmelding = sykmeldingKafkaMessage(fnr = "fnr", arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
        val sykmelding1 = sykmeldingKafkaMessage(fnr = "fnr", arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE)
        val sykmelding2 = sykmeldingKafkaMessage(fnr = "fnr", arbeidssituasjon = Arbeidssituasjon.FRILANSER)

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
                identer = FolkeregisterIdenter(originalIdent = sykmelding.kafkaMetadata.fnr, andreIdenter = emptyList()),
            ).also {
                it.size `should be equal to` 1
                it.single() `should be equal to` sykmelding1
            }
    }
}
