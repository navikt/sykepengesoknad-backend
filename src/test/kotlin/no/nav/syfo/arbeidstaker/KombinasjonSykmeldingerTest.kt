package no.nav.syfo.arbeidstaker

import no.nav.syfo.*
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate

class KombinasjonSykmeldingerTest : BaseTestClass() {

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    final val fnr = "123456789"

    val basisDato = LocalDate.of(2020, 3, 13)

    @Test
    fun `Splitter behandlingsdager og vanlig sykmelding`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "NAV")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato,
                        tom = basisDato.plusDays(2),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = 1,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(3),
                        tom = basisDato.plusDays(4),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                ).shuffled()
            )

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(2))
    }

    @Test
    fun `Splitter behandlingsdager og reisetilskudd`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "NAV")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato,
                        tom = basisDato.plusDays(2),
                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = 1,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(3),
                        tom = basisDato.plusDays(4),
                        type = PeriodetypeDTO.REISETILSKUDD,
                        reisetilskudd = true,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                ).shuffled()
            )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.BEHANDLINGSDAGER)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.REISETILSKUDD)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(2))
    }

    @Test
    fun `Splitter ikke gradert og 100 prosent`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "NAV")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato,
                        tom = basisDato.plusDays(2),
                        type = PeriodetypeDTO.GRADERT,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = GradertDTO(grad = 1, reisetilskudd = false),
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(3),
                        tom = basisDato.plusDays(4),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                ).shuffled()
            )
        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(4))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(2))
    }

    @Test
    fun `Splitter gradert reisetilskudd og 100 prosent`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "NAV")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato,
                        tom = basisDato.plusDays(2),
                        type = PeriodetypeDTO.GRADERT,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = GradertDTO(grad = 1, reisetilskudd = true),
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(3),
                        tom = basisDato.plusDays(4),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    )
                ).shuffled()
            )

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.GRADERT_REISETILSKUDD)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(4))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(2))
    }

    @Test
    fun `Splitter gradert reisetilskudd før 100 prosent og gradert`() {
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "NAV")
        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)
            .copy(
                sykmeldingsperioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato,
                        tom = basisDato.plusDays(2),
                        type = PeriodetypeDTO.GRADERT,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = GradertDTO(grad = 1, reisetilskudd = true),
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(3),
                        tom = basisDato.plusDays(4),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = null,
                        innspillTilArbeidsgiver = null
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = basisDato.plusDays(5),
                        tom = basisDato.plusDays(6),
                        type = PeriodetypeDTO.GRADERT,
                        reisetilskudd = false,
                        aktivitetIkkeMulig = null,
                        behandlingsdager = null,
                        gradert = GradertDTO(20, false),
                        innspillTilArbeidsgiver = null
                    )
                ).shuffled()
            )

        flexSyketilfelleMockRestServiceServer?.reset()
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val hentetViaRest = hentSoknader(fnr).filter { it.sykmeldingId == sykmeldingId }
        assertThat(hentetViaRest).hasSize(2)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.GRADERT_REISETILSKUDD)
        assertThat(hentetViaRest[0].fom).isEqualTo(basisDato)
        assertThat(hentetViaRest[0].tom).isEqualTo(basisDato.plusDays(2))
        assertThat(hentetViaRest[1].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[1].fom).isEqualTo(basisDato.plusDays(3))
        assertThat(hentetViaRest[1].tom).isEqualTo(basisDato.plusDays(6))

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(2))
    }
}
