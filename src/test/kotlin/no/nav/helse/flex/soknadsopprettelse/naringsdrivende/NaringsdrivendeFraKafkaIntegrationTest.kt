package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import com.nhaarman.mockitokotlin2.any
import no.nav.helse.flex.*
import no.nav.helse.flex.client.bregDirect.NAERINGSKODE_BARNEPASSER
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
import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.BrregMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_V2
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_OPPHOLD_I_UTLANDET
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VARIG_ENDRING
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET
import no.nav.helse.flex.soknadsopprettelse.OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerBladDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.*
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class NaringsdrivendeFraKafkaIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val dato = LocalDate.of(2025, 1, 1)

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
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

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = sykmeldingId, erUtenforVentetid = true)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
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
    fun `Oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
                .copy(harRedusertArbeidsgiverperiode = true)

        mockStandardSyketilfelle(sykmeldingId = sykmeldingId, erUtenforVentetid = false)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
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

        mockStandardSyketilfelle(sykmeldingId = sykmeldingId, erUtenforVentetid = false)
        settOppSigrunMockResponser()

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
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
                    selvstendigNaringsdrivendeDTO.inntekt `should not be equal to` null
                    selvstendigNaringsdrivendeDTO.brukerHarOppgittForsikring `should be equal to` true
                }
            }
        }

        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` RSArbeidssituasjon.NAERINGSDRIVENDE
            it.selvstendigNaringsdrivendeInfo!!.sykepengegrunnlagNaeringsdrivende `should not be equal to` null
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

        mockStandardSyketilfelle(sykmeldingId = sykmelding.id, erUtenforVentetid = true)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

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
                soknad.sporsmal!!.any { it.tag == NARINGSDRIVENDE_OPPHOLD_I_UTLANDET }.`should be true`()
                soknad.sporsmal.any { it.tag == NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT }.`should be true`()
            }

            hentSoknad(soknadId = metadata.last().id, fnr = fnr).also { soknad ->
                soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                soknad.fom `should be equal to` LocalDate.of(2020, 2, 23)
                soknad.tom `should be equal to` LocalDate.of(2020, 3, 15)
                soknad.sporsmal!!.any { it.tag == NARINGSDRIVENDE_OPPHOLD_I_UTLANDET }.`should be false`()
                soknad.sporsmal.any { it.tag == NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT }.`should be true`()
            }
        }
    }


    @Test
    fun `Oppretter søknad for næringsdrivende som er Barnepasser`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId).copy(harRedusertArbeidsgiverperiode = true)

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
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessageDTO(
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
                    NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
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
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessageDTO(
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
                    NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
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
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessageDTO(
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
                    NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
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
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmelding.id)

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessageDTO(
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
                    NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                    ANDRE_INNTEKTSKILDER,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
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
            SykmeldingKafkaMessageDTO(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockFlexSyketilfelleErUtenforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmelding.id)

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
                soknad.sporsmal!!.any { it.tag == NARINGSDRIVENDE_OPPHOLD_I_UTLANDET }.`should be true`()
                soknad.sporsmal.any { it.tag == NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT }.`should be true`()
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
                soknad.sporsmal!!.any { it.tag == NARINGSDRIVENDE_OPPHOLD_I_UTLANDET }.`should be false`()
                soknad.sporsmal.any { it.tag == NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT }.`should be true`()
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


    private fun opprettSykmeldingKafkaMelding(
        sykmelding: ArbeidsgiverSykmeldingDTO,
        event: SykmeldingStatusKafkaEventDTO,
        kafkaMetadata: KafkaMetadataDTO,
    ): SykmeldingKafkaMessageDTO =
        SykmeldingKafkaMessageDTO(
            sykmelding = sykmelding,
            event = event,
            kafkaMetadata = kafkaMetadata,
        )

    private fun mockStandardSyketilfelle(
        sykmeldingId: String,
        erUtenforVentetid: Boolean? = null,
        oppfolgingsdato: LocalDate = dato,
    ) {
        erUtenforVentetid?.let {
            mockFlexSyketilfelleErUtenforVentetid(sykmeldingId, it)
        }
        mockFlexSyketilfelleSykeforloep(sykmeldingId, oppfolgingsdato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmeldingId)
    }

    private fun settOppStandardNaeringsdrivendeData() {
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
    }


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
