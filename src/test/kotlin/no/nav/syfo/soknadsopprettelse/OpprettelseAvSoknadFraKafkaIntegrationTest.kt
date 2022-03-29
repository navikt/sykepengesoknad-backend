package no.nav.syfo.soknadsopprettelse

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.syfo.BaseTestClass
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadsperiode
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykmeldingstype
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.exception.ManglerSykmeldingException
import no.nav.syfo.domain.exception.ProduserKafkaMeldingException
import no.nav.syfo.domain.exception.RestFeilerException
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockFlexSyketilfelleErUtaforVentetid
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.MethodName::class)
class OpprettelseAvSoknadFraKafkaIntegrationTest : BaseTestClass() {

    private val fnr = "123456789"
    private val aktor = fnr + "00"

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        flexSyketilfelleMockRestServiceServer?.reset()
    }

    @AfterEach
    fun tearDown() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `oppretter kort søknad for næringsdrivende`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingId)).thenReturn(sykmelding)
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, false)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingId)).thenReturn(sykmelding)
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(0)

        verifyNoMoreInteractions(aivenKafkaProducer)
    }

    @Test
    fun `oppretter søknad for næringsdrivende når sykmeldingen er innenfor ventetiden MEN brukeren har forsikring`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val event = sykmeldingStatusKafkaMessageDTO.event.copy(
            sporsmals = listOf(
                SporsmalOgSvarDTO(
                    tekst = "Harru forsikring?",
                    svartype = SvartypeDTO.JA_NEI,
                    shortName = ShortNameDTO.FORSIKRING,
                    svar = "JA"
                ),
                sykmeldingStatusKafkaMessageDTO.event.sporsmals!![0]
            )
        )
        val sykmeldingId = event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingId)).thenReturn(sykmelding)
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)

        verify(aivenKafkaProducer, times(1)).produserMelding(any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    fun `oppretter ikke søknad for næringsdrivende når sykmeldingen er innenfor ventetiden og brukeren ikke har forsikring`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val event = sykmeldingStatusKafkaMessageDTO.event.copy(
            sporsmals = listOf(
                SporsmalOgSvarDTO(
                    tekst = "Harru forsikring?",
                    svartype = SvartypeDTO.JA_NEI,
                    shortName = ShortNameDTO.FORSIKRING,
                    svar = "NEI"
                ),
                sykmeldingStatusKafkaMessageDTO.event.sporsmals!![0]
            )
        )
        val sykmeldingId = event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, false)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingId)).thenReturn(sykmelding)
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(0)
        verifyNoMoreInteractions(aivenKafkaProducer)
    }

    @Test
    fun `oppretter 2 søknader for næringsdrivende når sykmeldigna er lengre enn 31 dager`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            fom = LocalDate.of(2020, 2, 1),
            tom = LocalDate.of(2020, 3, 15),
        )
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(hentetViaRest[0].fom).isEqualTo(LocalDate.of(2020, 2, 1))
        assertThat(hentetViaRest[0].tom).isEqualTo(LocalDate.of(2020, 2, 22))
        assertThat(hentetViaRest[1].fom).isEqualTo(LocalDate.of(2020, 2, 23))
        assertThat(hentetViaRest[1].tom).isEqualTo(LocalDate.of(2020, 3, 15))

        assertThat(hentetViaRest[0].sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()
        assertThat(hentetViaRest[1].sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `oppretter ingen søknader når sykmeldinga ikke har perioder`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
            fom = LocalDate.of(2020, 2, 1),
            tom = LocalDate.of(2020, 3, 15),
        )
            .copy(sykmeldingsperioder = emptyList())

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlig og behandlingsdager`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2020, 5, 4),
                        tom = LocalDate.of(2020, 5, 18),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2019, 3, 28),
                        tom = LocalDate.of(2020, 1, 6),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = 4,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                )
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(12)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 12)
    }

    @Test
    fun `oppretter ikke søknader for hull i sykmeldingens perioder mellom vanlige søknader`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2020, 5, 1),
                        tom = LocalDate.of(2020, 5, 1),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2019, 1, 1),
                        tom = LocalDate.of(2019, 1, 1),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                )
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `oppretter søknad for gradert reisetilskudd`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                harRedusertArbeidsgiverperiode = true,
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 3, 15),
                        type = PeriodetypeDTO.GRADERT,
                        gradert = GradertDTO(42, true),
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null
                    )
                )
            )
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `oppretter ikke søknad for avventende sykmelding`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                harRedusertArbeidsgiverperiode = true,
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 3, 15),
                        type = PeriodetypeDTO.AVVENTENDE,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                )
            )

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `oppretter ikke søknad for sykmelding under behandling`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        )
            .copy(
                merknader = listOf(Merknad(type = "UNDER_BEHANDLING", beskrivelse = "Manuell behandling :("))
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(0)
    }

    @Test
    fun `oppretter 2 søknader for næringsdrivende hvor gradering endres midt i når sykmeldigna er lengre enn 31 dager`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
        ).copy(
            harRedusertArbeidsgiverperiode = true,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 2, 5),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                ),
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.of(2020, 2, 6),
                    tom = LocalDate.of(2020, 3, 15),
                    type = PeriodetypeDTO.GRADERT,
                    gradert = GradertDTO(30, false),
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).sortedBy { it.fom }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE)
        assertThat(hentetViaRest[0].fom).isEqualTo(LocalDate.of(2020, 2, 1))
        assertThat(hentetViaRest[0].tom).isEqualTo(LocalDate.of(2020, 2, 22))
        assertThat(hentetViaRest[0].soknadPerioder).isEqualTo(
            listOf(
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 1),
                    tom = LocalDate.of(2020, 2, 5),
                    grad = 100,
                    sykmeldingstype = RSSykmeldingstype.AKTIVITET_IKKE_MULIG
                ),
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 6),
                    tom = LocalDate.of(2020, 2, 22),
                    grad = 30,
                    sykmeldingstype = RSSykmeldingstype.GRADERT
                )
            )
        )

        assertThat(hentetViaRest[1].fom).isEqualTo(LocalDate.of(2020, 2, 23))
        assertThat(hentetViaRest[1].tom).isEqualTo(LocalDate.of(2020, 3, 15))
        assertThat(hentetViaRest[1].soknadPerioder).isEqualTo(
            listOf(
                RSSoknadsperiode(
                    fom = LocalDate.of(2020, 2, 23),
                    tom = LocalDate.of(2020, 3, 15),
                    grad = 30,
                    sykmeldingstype = RSSykmeldingstype.GRADERT
                )
            )
        )

        assertThat(hentetViaRest[0].sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isTrue()
        assertThat(hentetViaRest[1].sporsmal!!.any { it.tag == ARBEID_UTENFOR_NORGE }).isFalse()
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(5))
    }

    @Test
    fun `legger til rebehandling UventetArbeidssituasjonException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(arbeidssituasjon = Arbeidssituasjon.FRILANSER)
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `legger til rebehandling ManglerArbeidsgiverException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `legger til rebehandling RestFeilerException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenThrow(RestFeilerException())
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `legger til rebehandling SykmeldingManglerPeriodeException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(
            sykmeldingStatusKafkaMessageDTO,
            syketilfelleStartDato = null,
            sykmeldingsperioder = emptyList()
        )

        whenever(syfoSmRegisterClient.hentSykmelding(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)).thenReturn(
            sykmelding
        )
        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `legger til rebehandling ManglerSykmeldingException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        doThrow(ManglerSykmeldingException())
            .whenever(aivenKafkaProducer)
            .produserMelding(
                any(),
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `legger til rebehandling ProduserKafkaMeldingException`() {
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding()
        val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)

        doThrow(ProduserKafkaMeldingException())
            .whenever(aivenKafkaProducer)
            .produserMelding(
                any()
            )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleSykeforloep(sykmeldingStatusKafkaMessageDTO.event.sykmeldingId)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
    }

    @Test
    fun `Uventet exception kastes videre`() {
        assertThrows(RuntimeException::class.java) {

            val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(
                statusEvent = STATUS_BEKREFTET,
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE
            )
            val sykmelding = skapSykmeldingDTO(sykmeldingStatusKafkaMessageDTO)
            mockFlexSyketilfelleSykeforloep(sykmelding.id)

            whenever(aivenKafkaProducer.produserMelding(any())).thenThrow(RuntimeException("Feil"))
            val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
            )

            behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)
        }
    }

    @Test
    fun `Sykmelding oppretter søknader og den legges til i databasen`() {
        val dato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(
            statusEvent = STATUS_BEKREFTET,
            arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE
        )
        val sykmelding = skapSykmeldingDTO(
            sykmeldingStatusKafkaMessageDTO,
            syketilfelleStartDato = dato,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = dato,
                    tom = dato.plusDays(40),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size).isEqualTo(2)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    fun `Sykmelding som feiler kjører rollback i databasen uten å kaste en ny UnexpectedRollbackException`() {
        val dato = LocalDate.now()
        val sykmeldingStatusKafkaMessageDTO = skapKafkaMelding(
            statusEvent = STATUS_BEKREFTET,
            arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE
        )
        val sykmelding = skapSykmeldingDTO(
            sykmeldingStatusKafkaMessageDTO,
            syketilfelleStartDato = dato,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = dato,
                    tom = dato.plusDays(40),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )
        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        // Kaster exception for søknad nr 2

        doThrow(ProduserKafkaMeldingException()).`when`(aivenKafkaProducer).produserMelding(
            argWhere { it.fom == dato.plusDays(21) && it.tom == dato.plusDays(40) }
        )

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmelding.id, sykmeldingKafkaMessage)

        assertThat(sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr)).size).isEqualTo(0)
        verify(rebehandlingsSykmeldingSendtProducer, times(1)).leggPaRebehandlingTopic(any(), any())
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    private fun skapKafkaMelding(
        statusEvent: String = STATUS_SENDT,
        arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER
    ) = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        statusEvent = statusEvent,
        arbeidssituasjon = arbeidssituasjon,
        arbeidsgiver = null
    )

    private fun skapSykmeldingDTO(
        sykmeldingStatusKafkaMessageDTO: SykmeldingStatusKafkaMessageDTO,
        syketilfelleStartDato: LocalDate? = LocalDate.of(2020, 2, 1),
        sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> = listOf(
            SykmeldingsperiodeAGDTO(
                fom = LocalDate.of(2020, 2, 1),
                tom = LocalDate.of(2020, 2, 5),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                reisetilskudd = false,
                aktivitetIkkeMulig = null,
                behandlingsdager = null,
                gradert = null,
                innspillTilArbeidsgiver = null
            )
        )
    ) = getSykmeldingDto(
        sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
    )
        .copy(
            sykmeldingsperioder = sykmeldingsperioder,
            syketilfelleStartDato = syketilfelleStartDato
        )
}
