package no.nav.helse.flex.soknadsopprettelse.naringsdrivende

import com.nhaarman.mockitokotlin2.any
import no.nav.helse.flex.*
import no.nav.helse.flex.client.bregDirect.NAERINGSKODE_BARNEPASSER
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.controller.domain.sykepengesoknad.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.sigrun404Feil
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.SykepengegrunnlagNaeringsdrivende
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.*
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldContainSame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime

class NaringsdrivendeFraKafkaIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val testDato = LocalDate.of(2025, 1, 1)
    private val arbeidUnderveis100ProsentTag = "ARBEID_UNDERVEIS_100_PROSENT_0"
    private val forventedeSporsmalTagsForNaringsdrivendeForsteSoknad =
        listOf(
            ANSVARSERKLARING,
            FRAVAR_FOR_SYKMELDINGEN_V2,
            TILBAKE_I_ARBEID,
            arbeidUnderveis100ProsentTag,
            NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
            ANDRE_INNTEKTSKILDER,
            OPPHOLD_UTENFOR_EOS,
            NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
            NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
            NARINGSDRIVENDE_VARIG_ENDRING,
            TIL_SLUTT,
        )
    private val forventedeSporsmalTagsForNaringsdrivendeAndreSoknad =
        forventedeSporsmalTagsForNaringsdrivendeForsteSoknad.minus(
            listOf(
                FRAVAR_FOR_SYKMELDINGEN_V2,
                NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                NARINGSDRIVENDE_VARIG_ENDRING,
            ),
        )

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

    @Nested
    inner class VentetidOgForsikring {
        @Test
        fun `Oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden`() {
            val utenforVentetid = false
            val testdata = opprettTestdata()

            mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = utenforVentetid, oppfolgingsdato = testDato)

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
                    sykmelding = testdata.sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                )

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                testdata.sykmeldingId,
                sykmeldingKafkaMessage,
                SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).size `should be equal to` 0
            verifyNoMoreInteractions(aivenKafkaProducer)
        }

        @Test
        fun `Oppretter søknad for næringsdrivende som er utenfor ventetiden`() {
            val utenforVentetid = true
            val testdata = opprettTestdata()

            settOppStandardNaeringsdrivendeData()
            mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = utenforVentetid, oppfolgingsdato = testDato)

            val kafkaSoknad =
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = testdata.sykmeldingId,
                    sykmelding = testdata.sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                )

            verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
                sykepengesoknadDTO = kafkaSoknad,
                forventetArbeidssituasjon = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
            )

            verifiserLagretNaringsdrivendeSoknad(forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE)

            verify(aivenKafkaProducer, times(1)).produserMelding(any())
        }

        @Test
        fun `Oppretter søknad for næringsdrivende når sykmeldingen er innenfor ventetiden MEN brukeren har forsikring`() {
            val utenforVentetid = false
            val forsikring = true
            val testdata = opprettTestdata()

            mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = utenforVentetid, oppfolgingsdato = testDato)

            val kafkaSoknad =
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = testdata.sykmeldingId,
                    sykmelding = testdata.sykmelding,
                    event = testdata.sykmeldingStatus.event.medForsikringssvar(),
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                )

            verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
                sykepengesoknadDTO = kafkaSoknad,
                forventetArbeidssituasjon = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
            )

            kafkaSoknad.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                selvstendigNaringsdrivendeDTO.inntekt `should not be equal to` null
                selvstendigNaringsdrivendeDTO.brukerHarOppgittForsikring `should be equal to` true
            }

            verifiserLagretNaringsdrivendeSoknad(
                forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
                forsikring = forsikring,
                verifiserSykepengegrunnlag = { it `should not be equal to` null },
            )

            verify(aivenKafkaProducer, times(1)).produserMelding(any())
        }
    }

    @Nested
    inner class StandardflytOgSporsmal {
        @Test
        fun `Oppretter søknad for næringsdrivende med oppdelte hovedspørsmål`() {
            val testdata = opprettTestdata()

            settOppStandardNaeringsdrivendeData()
            mockStandardSyketilfelle(testdata.sykmeldingId, oppfolgingsdato = testDato)

            val kafkaSoknad =
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = testdata.sykmeldingId,
                    sykmelding = testdata.sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                )

            verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
                sykepengesoknadDTO = kafkaSoknad,
                forventetArbeidssituasjon = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
            )

            verifiserLagretNaringsdrivendeSoknad(
                forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
            )

            verify(aivenKafkaProducer, times(1)).produserMelding(any())
        }

        @Test
        fun `Legger til ny-i-arbeidslivet-spørsmål når det ikke finnes inntekt før sykepengegrunnlaget`() {
            val testdata = opprettTestdata()

            repeat(5) {
                SigrunMockDispatcher.enqueueResponse(sigrun404Feil())
            }
            mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = true, oppfolgingsdato = testDato)

            val kafkaSoknad =
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = testdata.sykmeldingId,
                    sykmelding = testdata.sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                )

            verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
                sykepengesoknadDTO = kafkaSoknad,
                forventetArbeidssituasjon = ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE,
            )

            val lagretSoknad =
                verifiserLagretNaringsdrivendeSoknad(
                    forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
                    harFunnetInntektFoerSykepengegrunnlaget = false,
                    forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivendeForsteSoknad.plus(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET),
                    verifiserSykepengegrunnlag = {
                        it.`should not be null`()
                        it.harFunnetInntektFoerSykepengegrunnlaget `should be equal to` false
                        it.inntekter.map { inntekt -> inntekt.inntektsaar } `should be equal to` listOf("2023", "2022", "2021")
                        it.inntekter
                            .map { inntekt -> inntekt.pensjonsgivendeInntekt.isEmpty() } `should be equal to`
                            listOf(true, true, true)
                    },
                ).single()

            lagretSoknad.sporsmal!!
                .single { it.tag == NARINGSDRIVENDE_NY_I_ARBEIDSLIVET }
                .undersporsmal
                .single { it.tag == NARINGSDRIVENDE_NY_I_ARBEIDSLIVET_DATO }

            verify(aivenKafkaProducer, times(1)).produserMelding(any())
        }

        @Test
        fun `Oppretter ikke spørsmål om opphold i utlandet for sønkad nr 2 i samme syketilfelle`() {
            settOppStandardNaeringsdrivendeData()
            settOppStandardNaeringsdrivendeData()

            val testdata1 = opprettTestdata()
            val testdata2 = opprettTestdata(dato = testDato.plusDays(15))

            mockStandardSyketilfelle(
                testdata1.sykmeldingId,
                testdata2.sykmeldingId,
                erUtenforVentetid = true,
                oppfolgingsdato = testDato,
            )

            with(testdata1) {
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = this.sykmeldingId,
                    sykmelding = this.sykmelding,
                    event = this.sykmeldingStatus.event,
                    kafkaMetadata = this.sykmeldingStatus.kafkaMetadata,
                )
            }

            with(testdata2) {
                prosesserSykmeldingOgHentKafkaSoknad(
                    sykmeldingId = this.sykmeldingId,
                    sykmelding = this.sykmelding,
                    event = this.sykmeldingStatus.event,
                    kafkaMetadata = this.sykmeldingStatus.kafkaMetadata,
                )
            }

            verifiserLagretNaringsdrivendeSoknad(
                forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
                forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivendeAndreSoknad,
                antallSoknader = 2,
            )

            verify(aivenKafkaProducer, times(2)).produserMelding(any())
        }
    }

    @Nested
    inner class Arbeidssituasjonsvarianter {
        @Test
        fun `Oppretter søknad for Barnepasser`() {
            mockBarnepasserNaeringskode()
            lagEnSoknadOgVerifiser(
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                forventetArbeidssituasjonDTO = ArbeidssituasjonDTO.BARNEPASSER,
                forventetArbeidssituasjonRS = RSArbeidssituasjon.BARNEPASSER,
            )
        }

        @Test
        fun `Oppretter søknad for Jordbruker`() {
            lagEnSoknadOgVerifiser(
                arbeidssituasjon = Arbeidssituasjon.JORDBRUKER,
                forventetArbeidssituasjonDTO = ArbeidssituasjonDTO.JORDBRUKER,
                forventetArbeidssituasjonRS = RSArbeidssituasjon.JORDBRUKER,
            )
        }

        @Test
        fun `Oppretter søknad for Fisker`() {
            lagEnSoknadOgVerifiser(
                arbeidssituasjon = Arbeidssituasjon.FISKER,
                forventetArbeidssituasjonDTO = ArbeidssituasjonDTO.FISKER,
                forventetArbeidssituasjonRS = RSArbeidssituasjon.FISKER,
            )
            verifiserFiskerblad()
        }
    }

    @Nested
    inner class Periodesplitting {
        @Test
        fun `Oppretter 2 søknader for næringsdrivende når sykmeldingen er lengre enn 31 dager`() {
            val testdata = opprettTestdata()
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = testdata.sykmeldingId,
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 3, 15),
                )

            mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = true, oppfolgingsdato = testDato)
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                testdata.sykmeldingId,
                SykmeldingKafkaMessageDTO(
                    sykmelding = sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                ),
                SYKMELDINGSENDT_TOPIC,
            )
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

            val (forste, andre) = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            verifiserSoknadPerioder(
                soknadId = forste.id,
                forventedePerioder =
                    listOf(
                        RSSoknadsperiode(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 2, 22),
                            grad = 100,
                            sykmeldingstype = RSSykmeldingstype.AKTIVITET_IKKE_MULIG,
                        ),
                    ),
            )
            verifiserSoknadPerioder(
                soknadId = andre.id,
                forventedePerioder =
                    listOf(
                        RSSoknadsperiode(
                            fom = LocalDate.of(2020, 2, 23),
                            tom = LocalDate.of(2020, 3, 15),
                            grad = 100,
                            sykmeldingstype = RSSykmeldingstype.AKTIVITET_IKKE_MULIG,
                        ),
                    ),
            )
        }

        @Test
        fun `Oppretter 2 søknader for næringsdrivende hvor gradering endres midt i for sykmeldingen lengre enn 31 dager`() {
            val testdata = opprettTestdata()
            val sykmelding =
                skapArbeidsgiverSykmelding(sykmeldingId = testdata.sykmeldingId).copy(
                    sykmeldingsperioder =
                        listOf(
                            SykmeldingsperiodeAGDTO(
                                fom = LocalDate.of(2020, 2, 1),
                                tom = LocalDate.of(2020, 2, 5),
                                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                                gradert = null,
                                reisetilskudd = false,
                                aktivitetIkkeMulig = null,
                                behandlingsdager = null,
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

            mockStandardSyketilfelle(testdata.sykmeldingId, oppfolgingsdato = testDato)
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                testdata.sykmeldingId,
                SykmeldingKafkaMessageDTO(
                    sykmelding = sykmelding,
                    event = testdata.sykmeldingStatus.event,
                    kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
                ),
                SYKMELDINGSENDT_TOPIC,
            )
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

            val (forste, andre) = hentSoknaderMetadata(fnr).sortedBy { it.fom }
            verifiserSoknadPerioder(
                soknadId = forste.id,
                forventedePerioder =
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
            verifiserSoknadPerioder(
                soknadId = andre.id,
                forventedePerioder =
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

        private fun verifiserSoknadPerioder(
            soknadId: String,
            forventedePerioder: List<RSSoknadsperiode>,
        ) {
            hentSoknad(soknadId = soknadId, fnr = fnr).also { soknad ->
                soknad.fom `should be equal to` forventedePerioder.first().fom
                soknad.tom `should be equal to` forventedePerioder.last().tom
                soknad.soknadPerioder `should be equal to` forventedePerioder
            }
        }
    }

    private data class NaringsdrivendeTestdata(
        val sykmeldingStatus: SykmeldingStatusKafkaMessageDTO,
        val sykmelding: ArbeidsgiverSykmeldingDTO,
    ) {
        val sykmeldingId: String = sykmeldingStatus.event.sykmeldingId
    }

    private fun opprettTestdata(
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
        dato: LocalDate = testDato,
    ): NaringsdrivendeTestdata {
        val sykmeldingStatus =
            skapSykmeldingStatusKafkaMessageDTO(
                arbeidssituasjon = arbeidssituasjon,
                fnr = fnr,
                timestamp = OffsetDateTime.parse(dato.toString() + "T00:00:00Z"),
            )
        return NaringsdrivendeTestdata(
            sykmeldingStatus = sykmeldingStatus,
            sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatus.event.sykmeldingId,
                    fom = dato,
                    tom = dato.plusDays(14),
                ),
        )
    }

    private fun lagEnSoknadOgVerifiser(
        arbeidssituasjon: Arbeidssituasjon,
        forventetArbeidssituasjonDTO: ArbeidssituasjonDTO,
        forventetArbeidssituasjonRS: RSArbeidssituasjon,
    ) {
        val testdata = opprettTestdata(arbeidssituasjon)

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(testdata.sykmeldingId, erUtenforVentetid = true, oppfolgingsdato = testDato)

        val kafkaSoknad =
            prosesserSykmeldingOgHentKafkaSoknad(
                sykmeldingId = testdata.sykmeldingId,
                sykmelding = testdata.sykmelding,
                event = testdata.sykmeldingStatus.event,
                kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
            )

        verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
            sykepengesoknadDTO = kafkaSoknad,
            forventetArbeidssituasjon = forventetArbeidssituasjonDTO,
        )

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = forventetArbeidssituasjonRS,
        )

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
    }

    private fun verifiserFiskerblad() {
        hentSoknaderMetadata(fnr).single().also {
            sykepengesoknadDAO.finnSykepengesoknad(it.id).fiskerBlad `should be equal to` FiskerBlad.A
        }
    }

    private fun SykmeldingStatusKafkaEventDTO.medForsikringssvar(): SykmeldingStatusKafkaEventDTO =
        copy(
            sporsmals =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        tekst = "Tekst ikke relevant",
                        svartype = SvartypeKafkaDTO.JA_NEI,
                        shortName = ShortNameKafkaDTO.FORSIKRING,
                        svar = "JA",
                    ),
                    sporsmals!![0],
                ),
        )

    private fun settOppStandardNaeringsdrivendeData() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(fnr, "${testDato.year - 1}")
            enqueueMockResponse(fnr, "${testDato.year - 2}")
            enqueueMockResponse(fnr, "${testDato.year - 3}")
            enqueueMockResponse(fnr, "${testDato.year - 4}")
        }
    }

    private fun mockBarnepasserNaeringskode() {
        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })
    }

    private fun prosesserSykmeldingOgHentKafkaSoknad(
        sykmeldingId: String,
        sykmelding: ArbeidsgiverSykmeldingDTO,
        event: SykmeldingStatusKafkaEventDTO,
        kafkaMetadata: KafkaMetadataDTO,
    ): SykepengesoknadDTO {
        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessageDTO(
                sykmelding = sykmelding,
                event = event,
                kafkaMetadata = kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        return sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .single()
            .value()
            .tilSykepengesoknadDTO()
    }

    private fun verifiserKafkaTypeArbeidssituasjonOgNaeringsinfo(
        sykepengesoknadDTO: SykepengesoknadDTO,
        forventetArbeidssituasjon: ArbeidssituasjonDTO,
    ) {
        sykepengesoknadDTO.arbeidssituasjon `should be equal to` forventetArbeidssituasjon
        sykepengesoknadDTO.type `should be equal to` SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE

        sykepengesoknadDTO.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
            selvstendigNaringsdrivendeDTO.roller.single().also { rolleDTO ->
                rolleDTO.orgnummer `should be equal to` "orgnummer"
                rolleDTO.rolletype `should be equal to` "INNH"
            }
            selvstendigNaringsdrivendeDTO.inntekt!!.inntektsAar.size `should be equal to` 3
        }
    }

    private fun verifiserLagretNaringsdrivendeSoknad(
        forventetArbeidssituasjon: RSArbeidssituasjon,
        forsikring: Boolean = false,
        forventedeSporsmalTags: List<String> = forventedeSporsmalTagsForNaringsdrivendeForsteSoknad,
        harFunnetInntektFoerSykepengegrunnlaget: Boolean = true,
        antallSoknader: Int = 1,
        verifiserSykepengegrunnlag: ((SykepengegrunnlagNaeringsdrivende?) -> Unit)? = null,
    ): List<RSSykepengesoknad> {
        hentSoknader(fnr).sortedBy { it.fom }.also { soknader ->
            soknader.size `should be equal to` antallSoknader

            soknader.forEach {
                it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
                it.arbeidssituasjon `should be equal to` forventetArbeidssituasjon
            }

            soknader.last().also {
                val sykepengegrunnlag = it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende

                if (verifiserSykepengegrunnlag == null) {
                    sykepengegrunnlag `should be equal to`
                        lagSykepengegrunnlagNaeringsdrivende(
                            fnr = fnr,
                            dato = testDato,
                            harFunnetInntektFoerSykepengegrunnlaget = harFunnetInntektFoerSykepengegrunnlaget,
                        )
                } else {
                    verifiserSykepengegrunnlag(sykepengegrunnlag)
                }
                it.sporsmal!!.map { sporsmal -> sporsmal.tag } shouldContainSame forventedeSporsmalTags
                it.selvstendigNaringsdrivendeInfo?.brukerHarOppgittForsikring `should be equal to` forsikring
            }

            return soknader
        }
    }
}

fun lagSykepengegrunnlagNaeringsdrivende(
    fnr: String = "123456789",
    dato: LocalDate = LocalDate.of(2025, 1, 1),
    harFunnetInntektFoerSykepengegrunnlaget: Boolean = false,
): SykepengegrunnlagNaeringsdrivende =
    SykepengegrunnlagNaeringsdrivende(
        inntekter =
            (dato.year - 1 downTo dato.year - 3)
                .map { aar ->
                    HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = aar.toString(),
                        pensjonsgivendeInntekt =
                            listOf(
                                PensjonsgivendeInntekt(
                                    datoForFastsetting = "$aar-07-17",
                                    skatteordning = Skatteordning.FASTLAND,
                                    pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                                ),
                            ),
                    )
                }.toList(),
        harFunnetInntektFoerSykepengegrunnlaget = harFunnetInntektFoerSykepengegrunnlaget,
    )
