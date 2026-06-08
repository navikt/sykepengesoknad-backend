package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sykmeldinger.SykmeldingerResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleErUtenforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.mockdispatcher.FlexSykmeldingMockDispatcher
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.lagSykmeldingsPerioder
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class KlipperFrilanserTest : FellesTestOppsett() {
    private final val basisdato = LocalDate.now().plusYears(1L)
    private final val eldreSignaturOgBehandletTidspunkt = basisdato.atStartOfDay().atOffset(ZoneOffset.UTC)
    private final val nyereSignaturOgBehandletTidspunkt = eldreSignaturOgBehandletTidspunkt.plusHours(1)
    private final val fnr = "11111111111"

    @Autowired
    private lateinit var klippMetrikkRepository: KlippMetrikkRepository

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
    }

    @Test
    fun `Frilanser sykmelding klipper overlappende frilansersøknad fullstendig`() {
        val meldingerPaKafka = mutableListOf<SykepengesoknadDTO>()

        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = eldreSignaturOgBehandletTidspunkt,
                signaturDato = eldreSignaturOgBehandletTidspunkt,
            ),
        ).also { meldingerPaKafka.addAll(it) }

        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = nyereSignaturOgBehandletTidspunkt,
                signaturDato = nyereSignaturOgBehandletTidspunkt,
            ),
            forventaSoknader = 2,
        ).also { meldingerPaKafka.addAll(it) }

        meldingerPaKafka.shouldHaveSize(3)

        meldingerPaKafka[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[0].type shouldBeEqualTo SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE
        meldingerPaKafka[0].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[0].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[1].status shouldBeEqualTo SoknadsstatusDTO.SLETTET
        meldingerPaKafka[1].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[1].tom shouldBeEqualTo basisdato.plusDays(10)

        meldingerPaKafka[2].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        meldingerPaKafka[2].type shouldBeEqualTo SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE
        meldingerPaKafka[2].fom shouldBeEqualTo basisdato.plusDays(5)
        meldingerPaKafka[2].tom shouldBeEqualTo basisdato.plusDays(10)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 1
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[0].fom shouldBeEqualTo basisdato.plusDays(5)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.plusDays(10)
        hentSoknad(hentetViaRest[0].id, fnr).klippet shouldBeEqualTo false

        val klippmetrikker = klippMetrikkRepository.findAll().toList().sortedBy { it.variant }
        klippmetrikker shouldHaveSize 1

        klippmetrikker[0].soknadstatus `should be equal to` "FREMTIDIG"
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_FOR_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    @Test
    fun `Frilanser sykmelding klipper ikke en overlappende arbeidstakersøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = eldreSignaturOgBehandletTidspunkt,
                signaturDato = eldreSignaturOgBehandletTidspunkt,
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(5),
                        tom = basisdato.plusDays(10),
                    ),
                sykmeldingSkrevet = nyereSignaturOgBehandletTidspunkt,
                signaturDato = nyereSignaturOgBehandletTidspunkt,
            ),
            forventaSoknader = 1,
        )

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest shouldHaveSize 2

        hentetViaRest.map { it.soknadstype } shouldContainSame
            listOf(
                RSSoknadstype.ARBEIDSTAKERE,
                RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE,
            )

        hentetViaRest.forEach {
            it.fom shouldBeEqualTo basisdato.plusDays(5)
            it.tom shouldBeEqualTo basisdato.plusDays(10)
            hentSoknad(it.id, fnr).klippet shouldBeEqualTo false
        }

        klippMetrikkRepository.findAll().toList().shouldHaveSize(0)
    }

    @Test
    fun `Frilanser ventetidssøknader som overlapper delvis klippes`() {
        flexSyketilfelleMockRestServiceServer.reset()
        val dato = LocalDate.of(2025, 1, 1)
        val sykmeldingSkrevetA = dato.atStartOfDay().atOffset(ZoneOffset.UTC)
        val sykmeldingSkrevetB = sykmeldingSkrevetA.plusHours(1)

        val sykmeldingA =
            lagFrilanserSykmelding(
                fom = dato,
                tom = dato.plusDays(10),
                sykmeldingSkrevet = sykmeldingSkrevetA,
                signaturDato = sykmeldingSkrevetA,
            )

        val sykmeldingB =
            lagFrilanserSykmelding(
                fom = dato.plusDays(5),
                tom = dato.plusDays(15),
                sykmeldingSkrevet = sykmeldingSkrevetB,
                signaturDato = sykmeldingSkrevetB,
            )

        mockFlexSyketilfelleErUtenforVentetid(sykmeldingId = sykmeldingA.sykmelding.id, erUtenforVentetid = false)
        mockFlexSyketilfelleErUtenforVentetid(sykmeldingId = sykmeldingB.sykmelding.id, erUtenforVentetid = true)

        mockFlexSyketilfelleSykeforloep(
            sykmeldingIder = setOf(sykmeldingA.sykmelding.id, sykmeldingB.sykmelding.id),
            oppfolgingsdato = dato,
        )

        mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(
            sykmeldingIder = setOf(sykmeldingB.sykmelding.id, sykmeldingA.sykmelding.id),
        )
        FlexSykmeldingMockDispatcher.enqueue(SykmeldingerResponse(listOf(sykmeldingA)))

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingA.sykmelding.id,
            sykmeldingKafkaMessage = sykmeldingA,
            topic = SYKMELDINGBEKREFTET_TOPIC,
        )
        hentSoknader(fnr).shouldHaveSize(0)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId = sykmeldingB.sykmelding.id,
            sykmeldingKafkaMessage = sykmeldingB,
            topic = SYKMELDINGBEKREFTET_TOPIC,
        )

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)

        val soknader = hentSoknader(fnr)
        soknader shouldHaveSize 2

        val soknadA = soknader.first { it.sykmeldingId == sykmeldingA.sykmelding.id }
        val soknadB = soknader.first { it.sykmeldingId == sykmeldingB.sykmelding.id }

        soknadA.ventetidSykmeldingUuid shouldBeEqualTo sykmeldingB.sykmelding.id
        soknadA.fom shouldBeEqualTo dato
        soknadA.tom shouldBeEqualTo dato.plusDays(4)

        soknadB.ventetidSykmeldingUuid.shouldBeNull()
        soknadB.fom shouldBeEqualTo dato.plusDays(5)
        soknadB.tom shouldBeEqualTo dato.plusDays(15)

        val klippmetrikker = klippMetrikkRepository.findAll().toList()
        klippmetrikker shouldHaveSize 1
        klippmetrikker[0].variant `should be equal to` "SOKNAD_STARTER_INNI_SLUTTER_ETTER"
        klippmetrikker[0].endringIUforegrad `should be equal to` "SAMME_UFØREGRAD"
        klippmetrikker[0].klippet `should be equal to` true
    }

    private fun lagFrilanserSykmelding(
        fom: LocalDate,
        tom: LocalDate,
        sykmeldingSkrevet: OffsetDateTime,
        signaturDato: OffsetDateTime = sykmeldingSkrevet,
    ): SykmeldingKafkaMessageDTO {
        val statusDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
            )
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = statusDTO.event.sykmeldingId,
                sykmeldingsperioder = lagSykmeldingsPerioder(fom = fom, tom = tom),
                sykmeldingSkrevet = sykmeldingSkrevet,
                signaturDato = signaturDato,
            )
        return SykmeldingKafkaMessageDTO(
            sykmelding = sykmelding,
            event = statusDTO.event,
            kafkaMetadata = statusDTO.kafkaMetadata,
        )
    }
}
