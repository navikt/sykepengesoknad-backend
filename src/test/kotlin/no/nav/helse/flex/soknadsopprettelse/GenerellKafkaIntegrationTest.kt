package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.exception.ManglerSykmeldingException
import no.nav.helse.flex.domain.exception.ProduserKafkaMeldingException
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleErUtenforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

class GenerellKafkaIntegrationTest : FellesTestOppsett() {
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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockStandardSyketilfelle(sykmelding.id)

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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockStandardSyketilfelle(sykmelding.id)

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

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

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

        hentSoknaderMetadata(fnr).`should be empty`()
    }

    @Test
    fun `Legger til rebehandling UventetArbeidssituasjonException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(arbeidssituasjon = Arbeidssituasjon.FRILANSER)
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

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
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling ManglerArbeidsgiverException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Legger til rebehandling RestFeilerException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
        mockStandardSyketilfelle(sykmelding.id)

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
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Uventet exception kastes videre`() {
        Assertions.assertThrows(RuntimeException::class.java) {
            val sykmeldingStatusKafkaMessageDTO =
                skapKafkaMelding(
                    statusEvent = STATUS_BEKREFTET,
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                )
            val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
            mockStandardSyketilfelle(sykmelding.id)

            whenever(aivenKafkaProducer.produserMelding(any())).thenThrow(RuntimeException("Feil"))
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
        }
    }

    @Test
    fun `Sykmelding oppretter søknader og den legges til i databasen`() {
        val lokalDato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO =
            skapKafkaMelding(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatusKafkaMessageDTO,
                syketilfelleStartDato = lokalDato,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = lokalDato,
                            tom = lokalDato.plusDays(40),
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
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )
        mockStandardSyketilfelle(sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = lokalDato)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        org.assertj.core.api.Assertions
            .assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size)
            .isEqualTo(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Sykmelding som feiler kjører rollback i databasen uten å kaste en ny UnexpectedRollbackException`() {
        val lokalDato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO =
            skapKafkaMelding(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            )
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatusKafkaMessageDTO,
                syketilfelleStartDato = lokalDato,
                sykmeldingsperioder =
                    listOf(
                        SykmeldingsperiodeAGDTO(
                            fom = lokalDato,
                            tom = lokalDato.plusDays(40),
                            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                            reisetilskudd = false,
                            aktivitetIkkeMulig = null,
                            behandlingsdager = null,
                            gradert = null,
                            innspillTilArbeidsgiver = null,
                        ),
                    ),
            )
        mockStandardSyketilfelle(sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = lokalDato)

        val sykmeldingKafkaMessage =
            opprettSykmeldingKafkaMelding(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
            argWhere {
                it.fom?.isEqual(lokalDato.plusDays(21)) == true &&
                    it.tom?.isEqual(lokalDato.plusDays(40)) == true
            },
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmelding.id,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        org.assertj.core.api.Assertions
            .assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size)
            .isEqualTo(0)
        Mockito.verify(rebehandlingsSykmeldingSendtProducer, Mockito.times(1)).leggPaRebehandlingTopic(any(), any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `Oppretter ikke søknad for næringsdrivende dersom sykmeldingen er brukt på en arbeidstakersøknad`() {
        val testDato = testDatoForNyeSoknader()
        val sykmeldingsperioder = lagSykmeldingsperioderForTest(testDato)
        val sykmeldingKafkaMessageArbeidstaker =
            lagSykmeldingKafkaMessageForTest(
                statusEvent = STATUS_SENDT,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                syketilfelleStartDato = testDato,
                sykmeldingsperioder = sykmeldingsperioder,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123456789", orgNavn = "ARBEIDSGIVER AS"),
            )
        val sykmeldingId = sykmeldingKafkaMessageArbeidstaker.sykmelding.id

        mockFlexSyketilfelleArbeidsgiverperiode()
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        prosesserSykmeldingFraKafka(sykmeldingId, sykmeldingKafkaMessageArbeidstaker)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        verifiserEnSoknadMedArbeidssituasjon(RSArbeidssituasjon.ARBEIDSTAKER)

        val sykmeldingKafkaMessageNaringsdrivende =
            lagSykmeldingKafkaMessageForTest(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                sykmeldingId = sykmeldingId,
                syketilfelleStartDato = testDato,
                sykmeldingsperioder = sykmeldingsperioder,
            )

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleErUtenforVentetid(sykmeldingKafkaMessageNaringsdrivende.sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmeldingKafkaMessageNaringsdrivende.sykmelding.id)

        prosesserSykmeldingFraKafka(sykmeldingId, sykmeldingKafkaMessageNaringsdrivende)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
        verifiserEnSoknadMedArbeidssituasjon(RSArbeidssituasjon.ARBEIDSTAKER)
    }

    @Test
    fun `Oppretter søknad og overskriver annen ikke-arbeidstakersøknad med samme sykmeldingId`() {
        val testDato = testDatoForNyeSoknader()
        val sykmeldingsperioder = lagSykmeldingsperioderForTest(testDato)
        val sykmeldingKafkaMessageArbeidsledig =
            lagSykmeldingKafkaMessageForTest(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                syketilfelleStartDato = testDato,
                sykmeldingsperioder = sykmeldingsperioder,
            )
        val sykmeldingId = sykmeldingKafkaMessageArbeidsledig.sykmelding.id

        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)

        prosesserSykmeldingFraKafka(sykmeldingId, sykmeldingKafkaMessageArbeidsledig)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        verifiserEnSoknadMedArbeidssituasjon(RSArbeidssituasjon.ARBEIDSLEDIG)

        val sykmeldingKafkaMessageAnnet =
            lagSykmeldingKafkaMessageForTest(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.ANNET,
                sykmeldingId = sykmeldingId,
                syketilfelleStartDato = testDato,
                sykmeldingsperioder = sykmeldingsperioder,
            )

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(sykmeldingId, dato)

        prosesserSykmeldingFraKafka(sykmeldingId, sykmeldingKafkaMessageAnnet)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        verifiserEnSoknadMedArbeidssituasjon(RSArbeidssituasjon.ANNET)
    }

    private fun testDatoForNyeSoknader(): LocalDate = LocalDate.of(2026, 1, 1).plusDays(10)

    private fun lagSykmeldingsperioderForTest(testDato: LocalDate): List<SykmeldingsperiodeAGDTO> =
        listOf(
            SykmeldingsperiodeAGDTO(
                fom = testDato,
                tom = testDato.plusDays(4),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                reisetilskudd = false,
                aktivitetIkkeMulig = null,
                behandlingsdager = null,
                gradert = null,
                innspillTilArbeidsgiver = null,
            ),
        )

    private fun lagSykmeldingKafkaMessageForTest(
        statusEvent: String,
        arbeidssituasjon: Arbeidssituasjon,
        syketilfelleStartDato: LocalDate,
        sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
        sykmeldingId: String? = null,
        arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    ): SykmeldingKafkaMessageDTO {
        val sykmeldingStatus =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                statusEvent = statusEvent,
                arbeidssituasjon = arbeidssituasjon,
                sykmeldingId = sykmeldingId ?: UUID.randomUUID().toString(),
                arbeidsgiver = arbeidsgiver,
            )
        val sykmelding =
            skapSykmeldingDTO(
                sykmeldingStatus,
                syketilfelleStartDato = syketilfelleStartDato,
                sykmeldingsperioder = sykmeldingsperioder,
            )

        return opprettSykmeldingKafkaMelding(
            sykmelding = sykmelding,
            event = sykmeldingStatus.event,
            kafkaMetadata = sykmeldingStatus.kafkaMetadata,
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

    private fun prosesserSykmeldingFraKafka(
        sykmeldingId: String,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ) {
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )
    }

    private fun verifiserEnSoknadMedArbeidssituasjon(forventetArbeidssituasjon: RSArbeidssituasjon) {
        hentSoknaderMetadata(fnr).shouldHaveSize(1)
        hentSoknader(fnr).single().arbeidssituasjon `should be equal to` forventetArbeidssituasjon
    }

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
}
