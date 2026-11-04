package no.nav.helse.flex.soknadsopprettelse

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSArbeidssituasjon
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.mockStandardSyketilfelle
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.*
import no.nav.helse.flex.ventPåRecords
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime

class GenerellKafkaIntegrationTest : FellesTestOppsett() {
    private val fnr = "123456789"
    private val datoIFremtiden = LocalDate.now().plusDays(1)

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

    @Nested
    inner class OppretterSoknad {
        @Test
        fun `Sykmelding oppretter 2 søknader og de legges til i databasen`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = datoIFremtiden,
                            tom = datoIFremtiden.plusDays(40),
                        ),
                    syketilfelleStartDato = datoIFremtiden,
                )

            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size shouldBe 2
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        }

        @Test
        fun `Oppretter 2 søknader for gradert reisetilskudd over 30 dager`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        gradertReisetilskudd(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            grad = 42,
                        ),
                )

            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).shouldHaveSize(2)
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        }
    }

    @Test
    fun `Oppretter søknad og overskriver annen ikke-arbeidstakersøknad med samme sykmeldingId`() {
        val perioder = heltSykmeldt(fom = datoIFremtiden, tom = datoIFremtiden.plusDays(4))

        val kafkaMessage =
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
                sykmeldingsperioder = perioder,
            )

        mockFlexSyketilfelleSykeforloep(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = kafkaMessage,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        hentSoknaderMetadata(fnr).shouldHaveSize(1)
        hentSoknader(fnr).single().arbeidssituasjon shouldBe RSArbeidssituasjon.ARBEIDSLEDIG

        val sykmeldingKafkaMessageAnnet =
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ANNET,
                fnr = fnr,
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingsperioder = perioder,
            )

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleSykeforloep(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = kafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage = sykmeldingKafkaMessageAnnet,
            topic = SYKMELDINGSENDT_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        hentSoknaderMetadata(fnr).shouldHaveSize(1)
        hentSoknader(fnr).single().arbeidssituasjon shouldBe RSArbeidssituasjon.ANNET
    }

    @Nested
    inner class OppretterIkkeSoknad {
        @Test
        fun `Oppretter ingen søknader når sykmeldingen ikke har perioder`() {
            val kafkaMessage = sykmeldingKafkaMessage(fnr = fnr, sykmeldingsperioder = emptyList())
            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).shouldBeEmpty()
        }

        @Test
        fun `Oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlig og behandlingsdager`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2020, 5, 4),
                            tom = LocalDate.of(2020, 5, 18),
                        ) +
                            behandlingsdager(
                                fom = LocalDate.of(2019, 3, 28),
                                tom = LocalDate.of(2020, 1, 6),
                                behandlingsdager = 4,
                            ),
                )
            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr)
                .shouldHaveSize(12)
                .count { it.soknadstype == RSSoknadstype.BEHANDLINGSDAGER } shouldBe 11
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 12)
        }

        @Test
        fun `Oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlige søknader`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2020, 5, 1),
                            tom = LocalDate.of(2020, 5, 1),
                        ) +
                            heltSykmeldt(
                                fom = LocalDate.of(2019, 1, 1),
                                tom = LocalDate.of(2019, 1, 1),
                            ),
                )
            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                kafkaMessage.sykmelding.id,
                kafkaMessage,
                SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).shouldHaveSize(2)
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        }

        @Test
        fun `Oppretter ikke søknad for avventende sykmelding`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        avventende(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                        ),
                )

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).shouldBeEmpty()
        }

        @Test
        @Suppress("ktlint:standard:max-line-length")
        fun `Oppretter ikke søknad hvis sykmelding har endret arbeidssituasjon til noe annet enn arbeidstaker når det allerede finnes en tilknyttet arbeidstakersøknad`() {
            val perioder = heltSykmeldt(fom = datoIFremtiden, tom = datoIFremtiden.plusDays(4))

            val kafkaMessage =
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    sykmeldingsperioder = perioder,
                )

            mockFlexSyketilfelleArbeidsgiverperiode()
            mockFlexSyketilfelleSykeforloep(kafkaMessage.sykmelding.id, datoIFremtiden)
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
            hentSoknaderMetadata(fnr).shouldHaveSize(1)
            hentSoknader(fnr).single().arbeidssituasjon shouldBe RSArbeidssituasjon.ARBEIDSTAKER

            val sykmeldingKafkaMessageNaringsdrivende =
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = fnr,
                    sykmeldingId = kafkaMessage.sykmelding.id,
                    sykmeldingsperioder = perioder,
                )

            flexSyketilfelleMockRestServiceServer.reset()
            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = datoIFremtiden)

            val logger =
                LoggerFactory.getLogger(OpprettSoknadService::class.java) as Logger
            val listAppender =
                ListAppender<ILoggingEvent>().also {
                    it.start()
                    logger.addAppender(it)
                }

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = sykmeldingKafkaMessageNaringsdrivende,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            hentSoknaderMetadata(fnr).shouldHaveSize(1)
            hentSoknader(fnr).single().arbeidssituasjon shouldBe RSArbeidssituasjon.ARBEIDSTAKER

            logger.detachAppender(listAppender)

            val forventetMelding =
                "Oppretter ikke søknader for sykmelding: ${sykmeldingKafkaMessageNaringsdrivende.sykmelding.id} med arbeidssituasjon NAERINGSDRIVENDE siden det finnes allerede en arbeidstaker søknad. Kan skje ved kafkalag."
            listAppender.list
                .any { it.level == Level.ERROR && it.formattedMessage.contains(forventetMelding) } shouldBe true
        }
    }

    @Nested
    inner class FeilHandtering {
        @Test
        fun `Uventet exception kastes videre`() {
            Assertions.assertThrows(RuntimeException::class.java) {
                val kafkaMessage = sykmeldingKafkaMessage(fnr = fnr)
                mockStandardSyketilfelle(kafkaMessage.sykmelding.id, oppfolgingsdato = datoIFremtiden)

                whenever(aivenKafkaProducer.produserMelding(any())).thenThrow(RuntimeException("Feil"))

                behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                    sykmeldingId = kafkaMessage.sykmelding.id,
                    sykmeldingKafkaMessage = kafkaMessage,
                    topic = SYKMELDINGSENDT_TOPIC,
                )
            }
        }

        @Test
        fun `Sykmelding som feiler kjører rollback i databasen uten å kaste en ny UnexpectedRollbackException`() {
            val kafkaMessage =
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = datoIFremtiden,
                            tom = datoIFremtiden.plusDays(40),
                        ),
                    syketilfelleStartDato = datoIFremtiden,
                )

            mockStandardSyketilfelle(kafkaMessage.sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = datoIFremtiden)

            // Kaster feil på andre sykepengesøknad som produseres
            doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
                argWhere {
                    it.tom?.isEqual(datoIFremtiden.plusDays(40)) == true
                },
            )

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmeldingId = kafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage = kafkaMessage,
                topic = SYKMELDINGSENDT_TOPIC,
            )

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size shouldBe 0
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        }

        private inner class ProduserKafkaMeldingException :
            SkalRebehandlesException("Feil ved produksjon av Kafka-melding", OffsetDateTime.now().plusMinutes(1))
    }
}
