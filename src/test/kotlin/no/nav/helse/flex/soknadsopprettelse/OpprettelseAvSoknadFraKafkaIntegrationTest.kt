package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.*
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadsperiode
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykmeldingstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.BrregRolle
import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.domain.exception.ManglerSykmeldingException
import no.nav.helse.flex.domain.exception.ProduserKafkaMeldingException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.enqueueMockResponse
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.AarVerdi
import no.nav.helse.flex.service.Beregnet
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerBladDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testoppsett.simpleDispatcher
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigInteger
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class OpprettelseAvSoknadFraKafkaIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer.reset()
    }

    @AfterEach
    fun tearDown() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Oppretter kort søknad for næringsdrivende`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val hentetViaRest = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(hentetViaRest[0].arbeidssituasjon).isEqualTo(RSArbeidssituasjon.NAERINGSDRIVENDE)

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter kort søknad for næringsdrivende med data fra brreg`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-brreg")

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        RollerDto(
                            roller =
                                listOf(
                                    RolleDto(
                                        rolletype = Rolletype.INNH,
                                        organisasjonsnummer = "orgnummer",
                                        organisasjonsnavn = "orgnavn",
                                    ),
                                ),
                        ).serialisertTilString(),
                    )
            }

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        hentSoknader(fnr).sortedBy { it.fom }.first().apply {
            this.selvstendigNaringsdrivendeInfo?.roller `should be equal to`
                listOf(
                    BrregRolle(
                        orgnummer = "orgnummer",
                        orgnavn = "orgnavn",
                        rolletype = "INNH",
                    ),
                )
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter kort søknad for næringsdrivende uten data fra brreg når feature toggle er av`() {
        fakeUnleash.resetAll()

        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        RollerDto(
                            roller =
                                listOf(
                                    RolleDto(
                                        rolletype = Rolletype.INNH,
                                        organisasjonsnummer = "orgnummer",
                                        organisasjonsnavn = "orgnavn",
                                    ),
                                ),
                        ).serialisertTilString(),
                    )
            }

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        hentSoknader(fnr).sortedBy { it.fom }.first().apply {
            this.selvstendigNaringsdrivendeInfo
                .shouldNotBeNull()
                .roller
                .shouldBeEmpty()
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter kort søknad for næringsdrivende med Sigrun-data`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        settOppSigrunMockResponser()

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        hentSoknader(fnr).sortedBy { it.fom }.first().apply {
            this.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()
        }
        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter kort søknad for næringsdrivende uten Sigrun-data når feature toggle er av`() {
        fakeUnleash.resetAll()
        settOppSigrunMockResponser()

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        hentSoknader(fnr).sortedBy { it.fom }.first().apply {
            this.selvstendigNaringsdrivendeInfo
                .`should not be null`()
                .sykepengegrunnlagNaeringsdrivende
                .`should be null`()
        }
        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter kort søknad for fisker`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.FISKER)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val soknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknad).hasSize(1)
        assertThat(soknad.first().fiskerBlad).isEqualTo(FiskerBladDTO.A)
        assertThat(soknad.first().type).isEqualTo(SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE)

        val hentetSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknad.first().id)
        assertThat(hentetSoknad.fiskerBlad).isNotNull
        assertThat(hentetSoknad.fiskerBlad).isEqualTo(FiskerBlad.A)

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, false)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(0)

        verifyNoMoreInteractions(aivenKafkaProducer)
    }

    @Test
    fun `Oppretter søknad for næringsdrivende når sykmeldingen er innenfor ventetiden MEN brukeren har forsikring`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val event =
            sykmeldingStatusKafkaMessageDTO.event.copy(
                sporsmals =
                    listOf(
                        SporsmalOgSvarKafkaDTO(
                            tekst = "Harru forsikring?",
                            svartype = SvartypeKafkaDTO.JA_NEI,
                            shortName = ShortNameKafkaDTO.FORSIKRING,
                            svar = "JA",
                        ),
                        sykmeldingStatusKafkaMessageDTO.event.sporsmals!![0],
                    ),
            )
        val sykmeldingId = event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, false)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden og brukeren ikke har forsikring`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val event =
            sykmeldingStatusKafkaMessageDTO.event.copy(
                sporsmals =
                    listOf(
                        SporsmalOgSvarKafkaDTO(
                            tekst = "Harru forsikring?",
                            svartype = SvartypeKafkaDTO.JA_NEI,
                            shortName = ShortNameKafkaDTO.FORSIKRING,
                            svar = "NEI",
                        ),
                        sykmeldingStatusKafkaMessageDTO.event.sporsmals!![0],
                    ),
            )
        val sykmeldingId = event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, false)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(0)
        verifyNoMoreInteractions(aivenKafkaProducer)
    }

    @Test
    fun `Oppretter 2 søknader for næringsdrivende når sykmeldigen er lengre enn 31 dager`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)

        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 3, 15),
            ).copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(soknaderMetadata).hasSize(2)

        val forsteSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )
        assertThat(forsteSoknad.soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(forsteSoknad.fom).isEqualTo(LocalDate.of(2020, 2, 1))
        assertThat(forsteSoknad.tom).isEqualTo(LocalDate.of(2020, 2, 22))
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()

        val andreSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.last().id,
                fnr = fnr,
            )
        assertThat(andreSoknad.fom).isEqualTo(LocalDate.of(2020, 2, 23))
        assertThat(andreSoknad.tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
    }

    @Test
    fun `Oppretter ingen søknader når sykmeldingen ikke har perioder`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 3, 15),
            ).copy(sykmeldingsperioder = emptyList())

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `Oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlig og behandlingsdager`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            ).copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 5, 4),
                            tom = LocalDate.of(2020, 5, 18),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2019, 3, 28),
                            tom = LocalDate.of(2020, 1, 6),
                            type = PeriodetypeDTO.BEHANDLINGSDAGER,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = 4,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(12)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 12)
    }

    @Test
    fun `Oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlige søknader`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            ).copy(
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 5, 1),
                            tom = LocalDate.of(2020, 5, 1),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2019, 1, 1),
                            tom = LocalDate.of(2019, 1, 1),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(2)
    }

    @Test
    fun `Oppretter søknad for gradert reisetilskudd`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            ).copy(
                harRedusertArbeidsgiverperiode = true,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            type = PeriodetypeDTO.GRADERT,
                            gradert = GradertDTO(42, true),
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(2)
    }

    @Test
    fun `Oppretter ikke søknad for avventende sykmelding`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            ).copy(
                harRedusertArbeidsgiverperiode = true,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            type = PeriodetypeDTO.AVVENTENDE,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `Oppretter 2 søknader for næringsdrivende hvor gradering endres midt i når sykmeldigna er lengre enn 31 dager`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            ).copy(
                harRedusertArbeidsgiverperiode = true,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 2, 5),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                        SykmeldingsperiodeAGDTO(
                            fom = LocalDate.of(2020, 2, 6),
                            tom = LocalDate.of(2020, 3, 15),
                            type = PeriodetypeDTO.GRADERT,
                            gradert = GradertDTO(30, false),
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val soknaderMetadata = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        assertThat(soknaderMetadata).hasSize(2)

        val forsteSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.first().id,
                fnr = fnr,
            )
        assertThat(forsteSoknad.soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(forsteSoknad.fom).isEqualTo(LocalDate.of(2020, 2, 1))
        assertThat(forsteSoknad.tom).isEqualTo(LocalDate.of(2020, 2, 22))
        assertThat(forsteSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()
        assertThat(forsteSoknad.soknadPerioder).isEqualTo(
            listOf(
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 2, 5),
                    grad = 100,
                    sykmeldingstype = RSSykmeldingstype.AKTIVITET_IKKE_MULIG,
                ),
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 6),
                    tom = LocalDate.of(2020, 2, 22),
                    grad = 30,
                    sykmeldingstype = RSSykmeldingstype.GRADERT,
                ),
            ),
        )

        val andreSoknad =
            hentSoknad(
                soknadId = soknaderMetadata.last().id,
                fnr = fnr,
            )
        assertThat(andreSoknad.fom).isEqualTo(LocalDate.of(2020, 2, 23))
        assertThat(andreSoknad.tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(andreSoknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
        assertThat(andreSoknad.soknadPerioder).isEqualTo(
            listOf(
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 23),
                    tom = LocalDate.of(2020, 3, 15),
                    grad = 30,
                    sykmeldingstype = RSSykmeldingstype.GRADERT,
                ),
            ),
        )
    }

    @Test
    fun `Legger til rebehandling UventetArbeidssituasjonException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(arbeidssituasjon = Arbeidssituasjon.FRILANSER)
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling ManglerArbeidsgiverException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling RestFeilerException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling SykmeldingManglerPeriodeException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatusKafkaMessageDTO,
                syketilfelleStartDato = null,
                sykmeldingsperioder = emptyList(),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling ManglerSykmeldingException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        doThrow(ManglerSykmeldingException())
            .whenever(aivenKafkaProducer)
            .produserMelding(
                any(),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling ProduserKafkaMeldingException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        doThrow(ProduserKafkaMeldingException())
            .whenever(aivenKafkaProducer)
            .produserMelding(
                any(),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Uventet exception kastes videre`() {
        assertThrows(RuntimeException::class.java) {
            val sykmeldingStatusKafkaMessageDTO =
                skapKafkaMelding(
                    statusEvent = STATUS_BEKREFTET,
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )
            val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
            mockFlexSyketilfelleSykeforloep(sykmelding.id)

            whenever(aivenKafkaProducer.produserMelding(any())).thenThrow(RuntimeException("Feil"))
            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessage(
                    sykmelding = sykmelding,
                    event = sykmeldingStatusKafkaMessageDTO.event,
                    kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
                )

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)
        }
    }

    @Test
    fun `Sykmelding oppretter søknader og den legges til i databasen`() {
        val dato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO =
            skapKafkaMelding(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatusKafkaMessageDTO,
                syketilfelleStartDato = dato,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = dato,
                            tom = dato.plusDays(40),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size).isEqualTo(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Sykmelding som feiler kjører rollback i databasen uten å kaste en ny UnexpectedRollbackException`() {
        val dato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO =
            skapKafkaMelding(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatusKafkaMessageDTO,
                syketilfelleStartDato = dato,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = dato,
                            tom = dato.plusDays(40),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        // Kaster exception for søknad nr 2
        doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
            argWhere { it ->
                it.fom?.isEqual(dato.plusDays(21)) == true &&
                    it.tom?.isEqual(dato.plusDays(40)) == true
            },
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage, SYKMELDINGSENDT_TOPIC)

        assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size).isEqualTo(0)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    private fun skapKafkaMelding(
        statusEvent: String = STATUS_SENDT,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
    ) = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        statusEvent = statusEvent,
        arbeidssituasjon = arbeidssituasjon,
        arbeidsgiver = null,
    )

    private fun skapSykmeldingDTO(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO,
        syketilfelleStartDato: LocalDate? = LocalDate.of(2020, 2, 1),
        sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> =
            listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 2, 5),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null,
                ),
            ),
    ) = skapArbeidsgiverSykmelding(
        sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
    ).copy(
        sykmeldingsperioder = sykmeldingsperioder,
        syketilfelleStartDato = syketilfelleStartDato,
    )

    private fun settOppSigrunMockResponser() {
        with(sigrunMockWebServer) {
            enqueueMockResponse(
                fnr = fnr,
                inntektsaar = "2024",
                inntekt =
                    emptyList(),
            )
            enqueueMockResponse(
                fnr = fnr,
                inntektsaar = "2023",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2023-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = fnr,
                inntektsaar = "2022",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2022-07-17",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                        ),
                    ),
            )
            enqueueMockResponse(
                fnr = fnr,
                inntektsaar = "2021",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2021-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 10_000,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 190_000,
                            pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 300_000,
                        ),
                    ),
            )
        }
    }
}

fun lagSykepengegrunnlagNaeringsdrivende() =
    SykepengegrunnlagNaeringsdrivende(
        gjennomsnittPerAar =
            listOf(
                AarVerdi(aar = "2023", verdi = BigInteger("851782")),
                AarVerdi(aar = "2022", verdi = BigInteger("872694")),
                AarVerdi(aar = "2021", verdi = BigInteger("890920")),
            ),
        grunnbeloepPerAar =
            listOf(
                AarVerdi(aar = "2021", verdi = BigInteger("104716")),
                AarVerdi(aar = "2022", verdi = BigInteger("109784")),
                AarVerdi(aar = "2023", verdi = BigInteger("116239")),
            ),
        grunnbeloepPaaSykmeldingstidspunkt = 124028,
        beregnetSnittOgEndring25 =
            Beregnet(
                snitt = BigInteger("871798"),
                p25 = BigInteger("1089748"),
                m25 = BigInteger("653849"),
            ),
        inntekter =
            listOf(
                HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2023",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = LocalDate.parse("2023-07-17").toString(),
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                            PensjonsgivendeInntekt(
                                datoForFastsetting = LocalDate.parse("2023-07-17").toString(),
                                skatteordning = Skatteordning.SVALBARD,
                                pensjonsgivendeInntektAvLoennsinntekt = 100_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 300_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                        ),
                ),
                HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2022",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = LocalDate.parse("2022-07-17").toString(),
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                            PensjonsgivendeInntekt(
                                datoForFastsetting = LocalDate.parse("2022-07-17").toString(),
                                skatteordning = Skatteordning.SVALBARD,
                                pensjonsgivendeInntektAvLoennsinntekt = 50_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 50_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 350_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 50_000,
                            ),
                        ),
                ),
                HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = "123456789",
                    inntektsaar = "2021",
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = LocalDate.parse("2021-07-17").toString(),
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 10_000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 190_000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 300_000,
                            ),
                        ),
                ),
            ),
    )
