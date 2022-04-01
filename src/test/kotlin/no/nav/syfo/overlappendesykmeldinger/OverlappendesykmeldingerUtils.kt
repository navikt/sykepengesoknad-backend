package no.nav.syfo.overlappendesykmeldinger

import no.nav.syfo.BaseTestClass
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
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
    )

    flexSyketilfelleMockRestServiceServer?.reset()
    mockFlexSyketilfelleSykeforloep(
        sykmelding.id,
        oppfolgingsdato
    )

    val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
        sykmelding = sykmelding,
        event = sykmeldingStatusKafkaMessageDTO.event,
        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
    )
    behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

    flexSyketilfelleMockRestServiceServer?.reset()
}
