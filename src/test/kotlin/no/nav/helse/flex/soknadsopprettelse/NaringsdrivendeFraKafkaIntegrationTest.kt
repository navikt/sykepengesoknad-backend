package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.*
import no.nav.helse.flex.client.bregDirect.NAERINGSKODE_BARNEPASSER
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.client.flexsyketilfelle.FomTomPeriode
import no.nav.helse.flex.client.flexsyketilfelle.VentetidResponse
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadsperiode
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykmeldingstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.domain.exception.ManglerSykmeldingException
import no.nav.helse.flex.domain.exception.ProduserKafkaMeldingException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.BrregMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.AarVerdi
import no.nav.helse.flex.service.Beregnet
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerBladDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.VentetidDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.*
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigInteger
import java.time.LocalDate

class NaringsdrivendeFraKafkaIntegrationTest : FellesTestOppsett() {
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
    fun `Oppretter søknad for næringsdrivende som er utenfor ventetiden`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        settOppSigrunMockResponser()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->
                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
                    selvstendigNaringsdrivendeDTO.brukerHarOppgittForsikring `should be equal to` false
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.NAERINGSDRIVENDE
            it.selvstendigNaringsdrivendeInfo!!.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()
            it.selvstendigNaringsdrivendeInfo.brukerHarOppgittForsikring `should be equal to` false
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter søknad for næringsdrivende som er utenfor ventetiden når Sigrun feature toggle er av`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->
                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt `should be equal to` null
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.NAERINGSDRIVENDE
            it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to` null
        }

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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).`should be empty`()

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
                            tekst = "Har du forsikring for sykmeldingsperioden?",
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
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->
                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt `should be equal to` null
                    selvstendigNaringsdrivendeDTO.brukerHarOppgittForsikring `should be equal to` true
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.NAERINGSDRIVENDE
            it.selvstendigNaringsdrivendeInfo!!.sykepengegrunnlagNaeringsdrivende `should be equal to` null
            it.selvstendigNaringsdrivendeInfo.brukerHarOppgittForsikring `should be equal to` true
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter 2 søknader for næringsdrivende når sykmeldingen er lengre enn 31 dager`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)

        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 3, 15),
            ).copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        repeat(2) {
            mockFlexSyketilfelleVentetid(
                sykmelding.id,
                VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
            )
        }
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).also {
            it.first().value().tilSykepengesoknadDTO().also { sykepengesoknadDTO ->
                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                }
            }

            it.last().value().tilSykepengesoknadDTO().also { sykepengesoknadDTO ->
                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                }
            }
        }

        hentSoknaderMetadata(fnr).sortedBy { it.fom }.also { metadata ->
            metadata.size `should be equal to` 2

            hentSoknad(soknadId = metadata.first().id, fnr = fnr).also { soknad ->
                soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                soknad.fom `should be equal to` LocalDate.of(2020, 2, 1)
                soknad.tom `should be equal to` LocalDate.of(2020, 2, 22)
                soknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }.`should be true`()
            }

            hentSoknad(soknadId = metadata.last().id, fnr = fnr).also { soknad ->
                soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                soknad.fom `should be equal to` LocalDate.of(2020, 2, 23)
                soknad.tom `should be equal to` LocalDate.of(2020, 3, 15)
                soknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }.`should be false`()
            }
        }
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).`should be empty`()
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).shouldHaveSize(12)
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).shouldHaveSize(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).shouldHaveSize(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).`should be empty`()
    }

    @Test
    fun `Oppretter søknad for næringsdrivende som er Barnepasser`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        settOppSigrunMockResponser()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->

                sykepengesoknadDTO.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.BARNEPASSER

                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.BARNEPASSER

            // Sikrer at vi henter inntektsopplysninger for BARNEPASSER.
            it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()

            // Sikrer at BARNEPASSER har spørsmål som eller blir stilt til NAERINGSDRIVENDE.
            it.sporsmal!!.map { sporsmal -> sporsmal.tag } `should be equal to`
                listOf(
                    ANSVARSERKLARING,
                    FRAVAR_FOR_SYKMELDINGEN_V2,
                    TILBAKE_I_ARBEID,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    ARBEID_UTENFOR_NORGE,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                    TIL_SLUTT,
                )
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter søknad for næringsdrivende med oppdelte hovedspørsmål`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                fnr = fnr,
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        fakeUnleash.enable("sykepengesoknad-backend-oppdelt-naringsdrivende")
        settOppSigrunMockResponser()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->

                sykepengesoknadDTO.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.NAERINGSDRIVENDE

            it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()

            it.sporsmal!!.map { sporsmal -> sporsmal.tag } `should be equal to`
                listOf(
                    ANSVARSERKLARING,
                    FRAVAR_FOR_SYKMELDINGEN_V2,
                    TILBAKE_I_ARBEID,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    ARBEID_UTENFOR_NORGE,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
                    TIL_SLUTT,
                )
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter søknad for Jordbruker`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                arbeidssituasjon = Arbeidssituasjon.JORDBRUKER,
                fnr = fnr,
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        settOppSigrunMockResponser()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->

                sykepengesoknadDTO.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.JORDBRUKER

                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.JORDBRUKER

            it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()

            it.sporsmal!!.map { sporsmal -> sporsmal.tag } `should be equal to`
                listOf(
                    ANSVARSERKLARING,
                    FRAVAR_FOR_SYKMELDINGEN_V2,
                    TILBAKE_I_ARBEID,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    ARBEID_UTENFOR_NORGE,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                    TIL_SLUTT,
                )
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter søknad for Fisker`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                arbeidssituasjon = Arbeidssituasjon.FISKER,
                fnr = fnr,
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-sigrun-paa-kafka")
        settOppSigrunMockResponser()

        BrregMockDispatcher.enqueue(
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            ),
        )

        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        mockFlexSyketilfelleVentetid(
            sykmelding.id,
            VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).single().also {
            sykepengesoknadDAO.finnSykepengesoknad(it.id).fiskerBlad `should be equal to` FiskerBlad.A
        }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            it.tilSykepengesoknadDTO().also { sykepengesoknadDTO ->

                sykepengesoknadDTO.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.FISKER
                sykepengesoknadDTO.fiskerBlad `should be equal to` FiskerBladDTO.A
                sykepengesoknadDTO.type `should be equal to` SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE

                sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                    selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                        rolleDTO.orgnummer `should be equal to` "orgnummer"
                        rolleDTO.rolletype `should be equal to` "INNH"
                    }
                    selvstendigNaringsdrivendeDTO.ventetid!! `should be equal to` VentetidDTO(fom, tom)
                    selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.FISKER

            it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende `should be equal to`
                lagSykepengegrunnlagNaeringsdrivende()

            it.sporsmal!!.map { sporsmal -> sporsmal.tag } `should be equal to`
                listOf(
                    ANSVARSERKLARING,
                    FRAVAR_FOR_SYKMELDINGEN_V2,
                    TILBAKE_I_ARBEID,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    ARBEID_UTENFOR_NORGE,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                    TIL_SLUTT,
                )
        }

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    @Test
    fun `Oppretter 2 søknader for næringsdrivende hvor gradering endres midt i for sykmeldingen lengre enn 31 dager`() {
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
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        repeat(2) {
            mockFlexSyketilfelleVentetid(
                sykmelding.id,
                VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
            )
        }
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        hentSoknaderMetadata(fnr).sortedBy { it.fom }.also { metadata ->
            metadata.size `should be equal to` 2

            hentSoknad(soknadId = metadata.first().id, fnr = fnr).also { soknad ->
                soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                soknad.fom `should be equal to` LocalDate.of(2020, 2, 1)
                soknad.tom `should be equal to` LocalDate.of(2020, 2, 22)
                soknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }.`should be true`()
                soknad.soknadPerioder `should be equal to`
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
                    )
            }

            hentSoknad(soknadId = metadata.last().id, fnr = fnr).also { soknad ->
                soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                soknad.fom `should be equal to` LocalDate.of(2020, 2, 23)
                soknad.tom `should be equal to` LocalDate.of(2020, 3, 15)
                soknad.sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }.`should be false`()
                soknad.soknadPerioder `should be equal to`
                    listOf(
                        RSSoknadsperiode(
                            fom = LocalDate.of(2020, 2, 23),
                            tom = LocalDate.of(2020, 3, 15),
                            grad = 30,
                            sykmeldingstype = RSSykmeldingstype.GRADERT,
                        ),
                    )
            }
        }
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

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

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
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

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmelding.id,
                sykmeldingKafkaMessage,
                SYKMELDINGSENDT_TOPIC,
            )
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
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        repeat(2) {
            mockFlexSyketilfelleVentetid(
                sykmelding.id,
                VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
            )
        }
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

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
        val (fom, tom) = sykmelding.sykmeldingsperioder.first()
        repeat(2) {
            mockFlexSyketilfelleVentetid(
                sykmelding.id,
                VentetidResponse(FomTomPeriode(fom = fom, tom = tom)),
            )
        }
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        // Kaster exception for søknad nr 2
        doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
            argWhere {
                it.fom?.isEqual(dato.plusDays(21)) == true &&
                    it.tom?.isEqual(dato.plusDays(40)) == true
            },
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

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
        with(SigrunMockDispatcher) {
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
            enqueueMockResponse(
                fnr = fnr,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                            pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 0,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                            pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 0,
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
