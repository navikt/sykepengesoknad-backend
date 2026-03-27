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
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
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
    private val arbeidUnderveis100ProsentTag = "ARBEID_UNDERVEIS_100_PROSENT_0"
    private val forventedeSporsmalTagsForNaringsdrivende =
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
            NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
            NARINGSDRIVENDE_VARIG_ENDRING,
            TIL_SLUTT,
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

    @Test
    fun `Oppretter søknad for næringsdrivende som er utenfor ventetiden`() {
        val testdata = opprettNaringsdrivendeTestdata()

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = true)

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = testdata.sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = testdata.sykmeldingStatus.event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            verifiserStandardKafkaMelding(it, ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE)
            it.selvstendigNaringsdrivende!!.brukerHarOppgittForsikring `should be equal to` false
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
            forventetForsikring = false,
        )

        verifiserEnKafkaMeldingProdusert()
    }

    @Test
    fun `Oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden`() {
        val testdata = opprettNaringsdrivendeTestdata()

        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = false)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = testdata.sykmelding,
                event = testdata.sykmeldingStatus.event,
                kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            testdata.sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        hentSoknaderMetadata(fnr).`should be empty`()

        verifyNoMoreInteractions(aivenKafkaProducer)
    }

    @Test
    fun `Oppretter søknad for næringsdrivende når sykmeldingen er innenfor ventetiden MEN brukeren har forsikring`() {
        val testdata = opprettNaringsdrivendeTestdata()
        val event = testdata.sykmeldingStatus.event.medForsikringssvar()
        val sykmeldingId = event.sykmeldingId

        mockStandardSyketilfelle(sykmeldingId = sykmeldingId, erUtenforVentetid = false)
        settOppSigrunMockResponser()

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            it.selvstendigNaringsdrivende!!.also { selvstendigNaringsdrivendeDTO ->
                selvstendigNaringsdrivendeDTO.inntekt `should not be equal to` null
                selvstendigNaringsdrivendeDTO.brukerHarOppgittForsikring `should be equal to` true
            }
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
            forventetForsikring = true,
            verifiserSykepengegrunnlag = { it `should not be equal to` null },
        )

        verifiserEnKafkaMeldingProdusert()
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

            verifiserTodeltNaringsdrivendeSoknad(
                soknadId = metadata.first().id,
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 2, 22),
                forventerOppholdUtland = true,
            )

            verifiserTodeltNaringsdrivendeSoknad(
                soknadId = metadata.last().id,
                fom = LocalDate.of(2020, 2, 23),
                tom = LocalDate.of(2020, 3, 15),
                forventerOppholdUtland = false,
            )
        }
    }

    @Test
    fun `Oppretter søknad for næringsdrivende som er Barnepasser`() {
        val testdata = opprettNaringsdrivendeTestdata()

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = true)
        mockBarnepasserNaeringskode()

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = testdata.sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = testdata.sykmeldingStatus.event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            verifiserStandardKafkaMelding(it, ArbeidssituasjonDTO.BARNEPASSER)
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.BARNEPASSER,
            forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivende,
        )

        verifiserEnKafkaMeldingProdusert()
    }

    @Test
    fun `Oppretter søknad for næringsdrivende med oppdelte hovedspørsmål`() {
        val testdata = opprettNaringsdrivendeTestdata()

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = true)

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = testdata.sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = testdata.sykmeldingStatus.event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            verifiserStandardKafkaMelding(it, ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE)
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.NAERINGSDRIVENDE,
            forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivende,
        )

        verifiserEnKafkaMeldingProdusert()
    }

    @Test
    fun `Oppretter søknad for Jordbruker`() {
        val testdata = opprettJordbrukerTestdata()

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = true)

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = testdata.sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = testdata.sykmeldingStatus.event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            verifiserStandardKafkaMelding(it, ArbeidssituasjonDTO.JORDBRUKER)
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.JORDBRUKER,
            forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivende,
        )

        verifiserEnKafkaMeldingProdusert()
    }

    @Test
    fun `Oppretter søknad for Fisker`() {
        val testdata = opprettFiskerTestdata()

        settOppStandardNaeringsdrivendeData()
        mockStandardSyketilfelle(sykmeldingId = testdata.sykmeldingId, erUtenforVentetid = true)

        prosesserSykmeldingOgVerifiserKafkaMelding(
            sykmeldingId = testdata.sykmeldingId,
            sykmelding = testdata.sykmelding,
            event = testdata.sykmeldingStatus.event,
            kafkaMetadata = testdata.sykmeldingStatus.kafkaMetadata,
        ) {
            verifiserStandardKafkaMelding(
                sykepengesoknadDTO = it,
                forventetArbeidssituasjon = ArbeidssituasjonDTO.FISKER,
                forventetFiskerBlad = FiskerBladDTO.A,
            )
        }

        hentSoknaderMetadata(fnr).single().also {
            sykepengesoknadDAO.finnSykepengesoknad(it.id).fiskerBlad `should be equal to` FiskerBlad.A
        }

        verifiserLagretNaringsdrivendeSoknad(
            forventetArbeidssituasjon = RSArbeidssituasjon.FISKER,
            forventedeSporsmalTags = forventedeSporsmalTagsForNaringsdrivende,
        )

        verifiserEnKafkaMeldingProdusert()
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

            verifiserTodeltNaringsdrivendeSoknad(
                soknadId = metadata.first().id,
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 2, 22),
                forventerOppholdUtland = true,
                forventedeSoknadsperioder =
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

            verifiserTodeltNaringsdrivendeSoknad(
                soknadId = metadata.last().id,
                fom = LocalDate.of(2020, 2, 23),
                tom = LocalDate.of(2020, 3, 15),
                forventerOppholdUtland = false,
                forventedeSoknadsperioder =
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
    }

    private data class NaringsdrivendeTestdata(
        val sykmeldingStatus: SykmeldingStatusKafkaMessageDTO,
        val sykmelding: ArbeidsgiverSykmeldingDTO,
    ) {
        val sykmeldingId: String = sykmeldingStatus.event.sykmeldingId
    }

    private fun opprettNaringsdrivendeTestdata() = opprettTestdata(Arbeidssituasjon.NAERINGSDRIVENDE)

    private fun opprettJordbrukerTestdata() = opprettTestdata(Arbeidssituasjon.JORDBRUKER)

    private fun opprettFiskerTestdata() = opprettTestdata(Arbeidssituasjon.FISKER)

    private fun opprettTestdata(arbeidssituasjon: Arbeidssituasjon): NaringsdrivendeTestdata {
        val sykmeldingStatus = skapSykmeldingStatusKafkaMessageDTO(arbeidssituasjon = arbeidssituasjon, fnr = fnr)
        return NaringsdrivendeTestdata(
            sykmeldingStatus = sykmeldingStatus,
            sykmelding =
                skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingStatus.event.sykmeldingId)
                    .copy(harRedusertArbeidsgiverperiode = true),
        )
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

    private fun SykmeldingStatusKafkaEventDTO.medForsikringssvar(): SykmeldingStatusKafkaEventDTO =
        copy(
            sporsmals =
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        tekst = "Har du forsikring for sykmeldingsperioden?",
                        svartype = SvartypeKafkaDTO.JA_NEI,
                        shortName = ShortNameKafkaDTO.FORSIKRING,
                        svar = "JA",
                    ),
                    sporsmals!![0],
                ),
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

    private fun mockBarnepasserNaeringskode() {
        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })
    }

    private fun prosesserSykmeldingOgVerifiserKafkaMelding(
        sykmeldingId: String,
        sykmelding: ArbeidsgiverSykmeldingDTO,
        event: SykmeldingStatusKafkaEventDTO,
        kafkaMetadata: KafkaMetadataDTO,
        verifikasjon: (sykepengesoknadDTO: SykepengesoknadDTO) -> Unit,
    ) {
        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = event,
                kafkaMetadata = kafkaMetadata,
            )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).single().value().also {
            verifikasjon(it.tilSykepengesoknadDTO())
        }
    }

    private fun verifiserStandardKafkaMelding(
        sykepengesoknadDTO: SykepengesoknadDTO,
        forventetArbeidssituasjon: ArbeidssituasjonDTO,
        forventetFiskerBlad: FiskerBladDTO? = null,
    ) {
        sykepengesoknadDTO.arbeidssituasjon `should be equal to` forventetArbeidssituasjon
        sykepengesoknadDTO.type `should be equal to` SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE
        forventetFiskerBlad?.let {
            sykepengesoknadDTO.fiskerBlad `should be equal to` it
        }

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
        forventedeSporsmalTags: List<String>? = null,
        forventetForsikring: Boolean? = null,
        verifiserSykepengegrunnlag: (SykepengegrunnlagNaeringsdrivende?) -> Unit = {
            it `should be equal to` lagSykepengegrunnlagNaeringsdrivende()
        },
    ) {
        hentSoknader(fnr).sortedBy { it.fom }.single().also {
            it.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            it.arbeidssituasjon `should be equal to` forventetArbeidssituasjon
            verifiserSykepengegrunnlag(it.selvstendigNaringsdrivendeInfo?.sykepengegrunnlagNaeringsdrivende)
            forventedeSporsmalTags?.let { tags ->
                it.sporsmal!!.map { sporsmal -> sporsmal.tag } `should be equal to` tags
            }
            forventetForsikring?.let { forsikring ->
                it.selvstendigNaringsdrivendeInfo?.brukerHarOppgittForsikring `should be equal to` forsikring
            }
        }
    }

    private fun verifiserTodeltNaringsdrivendeSoknad(
        soknadId: String,
        fom: LocalDate,
        tom: LocalDate,
        forventerOppholdUtland: Boolean,
        forventedeSoknadsperioder: List<RSSoknadsperiode>? = null,
    ) {
        hentSoknad(soknadId = soknadId, fnr = fnr).also { soknad ->
            soknad.soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
            soknad.fom `should be equal to` fom
            soknad.tom `should be equal to` tom
            soknad.sporsmal!!.any { it.tag == NARINGSDRIVENDE_OPPHOLD_I_UTLANDET } `should be equal to` forventerOppholdUtland
            soknad.sporsmal.any { it.tag == NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT }.`should be true`()
            forventedeSoknadsperioder?.let {
                soknad.soknadPerioder `should be equal to` it
            }
        }
    }

    private fun verifiserEnKafkaMeldingProdusert() = verify(aivenKafkaProducer, times(1)).produserMelding(any())

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
