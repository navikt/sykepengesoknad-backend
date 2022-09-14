package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import java.time.LocalDate
import java.util.*

fun BaseTestClass.sendArbeidstakerSykmelding(
    fom: LocalDate,
    tom: LocalDate,
    fnr: String,
    oppfolgingsdato: LocalDate = fom,
    gradert: GradertDTO? = null,
    sykmeldingId: String = UUID.randomUUID().toString(),
) {
    val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        statusEvent = STATUS_SENDT,
        arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Butikken"),
        sykmeldingId = sykmeldingId,
    )

    val sykmelding = getSykmeldingDto(
        sykmeldingId = sykmeldingId,
        fom = fom,
        tom = tom,
        gradert = gradert,
    )

    val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
        sykmelding = sykmelding,
        event = sykmeldingStatusKafkaMessageDTO.event,
        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
    )

    sendSykmelding(
        sykmeldingKafkaMessage,
        oppfolgingsdato,
    )
}

fun BaseTestClass.sendSykmelding(
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    oppfolgingsdato: LocalDate = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
) {
    flexSyketilfelleMockRestServiceServer?.reset()

    mockFlexSyketilfelleSykeforloep(
        sykmeldingKafkaMessage.sykmelding.id,
        oppfolgingsdato
    )

    behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)

    flexSyketilfelleMockRestServiceServer?.reset()
}
