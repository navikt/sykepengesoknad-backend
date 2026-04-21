package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockStandardSyketilfelle
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.*
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.*
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
        fakeUnleash.resetAll()
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

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size `should be equal to` 2
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

            hentSoknaderMetadata(fnr).`should be empty`()
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
                            behandingsdager(
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
                .count { it.soknadstype == RSSoknadstype.BEHANDLINGSDAGER } `should be equal to` 11
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

            hentSoknaderMetadata(fnr).`should be empty`()
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

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size `should be equal to` 0
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        }

        private inner class ProduserKafkaMeldingException :
            SkalRebehandlesException("Feil ved produksjon av Kafka-melding", OffsetDateTime.now().plusMinutes(1))
    }
}
