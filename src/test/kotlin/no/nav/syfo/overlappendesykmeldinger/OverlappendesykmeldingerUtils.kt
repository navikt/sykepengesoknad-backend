package no.nav.syfo.overlappendesykmeldinger

import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import java.time.LocalDate

internal fun BaseTestClass.sendArbeidstakerSykmelding(
    fom: LocalDate,
    tom: LocalDate,
    fnr: String,
    oppfolgingsdato: LocalDate = fom,
    gradert: GradertDTO? = null,
) {
    val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        statusEvent = STATUS_SENDT,
        arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Butikken")

    )
    val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
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

internal fun BaseTestClass.sendSykmelding(
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
