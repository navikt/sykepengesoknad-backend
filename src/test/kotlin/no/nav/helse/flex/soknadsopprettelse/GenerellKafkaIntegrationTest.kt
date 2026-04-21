package no.nav.helse.flex.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.*
import no.nav.helse.flex.domain.exception.SkalRebehandlesException
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.testdata.avventende
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.gradertReisetilskudd
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
            val sykmeldingStatusKafkaMessageDTO =
                skapSykmeldingStatusKafkaMessageDTO(
                    fnr = fnr,
                    statusEvent = STATUS_BEKREFTET,
                )
            val sykmelding =
                skapSykmeldingDTO(
                    sykmeldingStatusKafkaMessageDTO = sykmeldingStatusKafkaMessageDTO,
                    syketilfelleStartDato = datoIFremtiden,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = datoIFremtiden,
                            tom = datoIFremtiden.plusDays(40),
                        ),
                )

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
                    sykmelding = sykmelding,
                    event = sykmeldingStatusKafkaMessageDTO.event,
                    kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
                )

            mockStandardSyketilfelle(sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = datoIFremtiden)

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmelding.id,
                sykmeldingKafkaMessage,
                SYKMELDINGSENDT_TOPIC,
            )

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size `should be equal to` 2
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
        }

        @Test
        fun `Oppretter 2 søknader for gradert reisetilskudd over 30 dager`() {
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                ).copy(
                    harRedusertArbeidsgiverperiode = true,
                    sykmeldingsperioder =
                        gradertReisetilskudd(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                            grad = 42,
                        ),
                )

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
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
    }

    @Nested
    inner class OppretterIkkeSoknad {
        @Test
        fun `Oppretter ingen søknader når sykmeldingen ikke har perioder`() {
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                ).copy(sykmeldingsperioder = emptyList())

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
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
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                ).copy(
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

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
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
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                ).copy(
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

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
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
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmelding =
                skapArbeidsgiverSykmelding(
                    sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                ).copy(
                    harRedusertArbeidsgiverperiode = true,
                    sykmeldingsperioder =
                        avventende(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 3, 15),
                        ),
                )

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
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
    }

    @Nested
    inner class FeilHandtering {
        @Test
        fun `Uventet exception kastes videre`() {
            Assertions.assertThrows(RuntimeException::class.java) {
                val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
                val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
                mockStandardSyketilfelle(sykmelding.id)

                whenever(aivenKafkaProducer.produserMelding(any())).thenThrow(RuntimeException("Feil"))
                val sykmeldingKafkaMessage =
                    SykmeldingKafkaMessageDTO(
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
        fun `Sykmelding som feiler kjører rollback i databasen uten å kaste en ny UnexpectedRollbackException`() {
            val sykmeldingStatusKafkaMessageDTO =
                skapSykmeldingStatusKafkaMessageDTO(
                    fnr = fnr,
                    statusEvent = STATUS_BEKREFTET,
                )
            val sykmelding =
                skapSykmeldingDTO(
                    sykmeldingStatusKafkaMessageDTO,
                    syketilfelleStartDato = datoIFremtiden,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = datoIFremtiden,
                            tom = datoIFremtiden.plusDays(40),
                        ),
                )
            mockStandardSyketilfelle(sykmelding.id, erUtenforVentetid = true, oppfolgingsdato = datoIFremtiden)

            val sykmeldingKafkaMessage =
                SykmeldingKafkaMessageDTO(
                    sykmelding = sykmelding,
                    event = sykmeldingStatusKafkaMessageDTO.event,
                    kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
                )

            // Kaster feil på andre sykepengesøknad som produseres
            doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
                argWhere {
                    it.tom?.isEqual(datoIFremtiden.plusDays(40)) == true
                },
            )

            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
                sykmelding.id,
                sykmeldingKafkaMessage,
                SYKMELDINGSENDT_TOPIC,
            )

            sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size `should be equal to` 0
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        }

        private inner class ProduserKafkaMeldingException :
            SkalRebehandlesException("Feil ved produksjon av Kafka-melding", OffsetDateTime.now().plusMinutes(1))
    }

    private fun mockStandardSyketilfelle(
        sykmeldingId: String,
        erUtenforVentetid: Boolean? = null,
        oppfolgingsdato: LocalDate = datoIFremtiden,
    ) {
        erUtenforVentetid?.let {
            mockFlexSyketilfelleErUtenforVentetid(sykmeldingId, it)
        }
        mockFlexSyketilfelleSykeforloep(sykmeldingId, oppfolgingsdato)
        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmeldingId)
    }

    private fun skapSykmeldingDTO(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO,
        syketilfelleStartDato: LocalDate = LocalDate.of(2020, 2, 1),
        sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> =
            heltSykmeldt(
                fom = syketilfelleStartDato,
                tom = syketilfelleStartDato.plusDays(15),
            ),
    ) = skapArbeidsgiverSykmelding(
        sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
    ).copy(
        sykmeldingsperioder = sykmeldingsperioder,
        syketilfelleStartDato = syketilfelleStartDato,
    )
}
