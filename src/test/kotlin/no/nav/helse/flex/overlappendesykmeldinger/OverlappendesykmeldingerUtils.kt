package no.nav.helse.flex.overlappendesykmeldinger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import java.time.LocalDate
import java.util.*

@Deprecated("ikke bruk")
fun BaseTestClass.sendArbeidstakerSykmelding(
    fom: LocalDate,
    tom: LocalDate,
    fnr: String,
    oppfolgingsdato: LocalDate = fom,
    gradert: GradertDTO? = null,
    sykmeldingId: String = UUID.randomUUID().toString(),
    forventaSoknader: Int = 1
): List<SykepengesoknadDTO> {
    val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        statusEvent = STATUS_SENDT,
        arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Butikken"),
        sykmeldingId = sykmeldingId,
    )

    val sykmelding = skapArbeidsgiverSykmelding(
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

    return sendSykmelding(
        sykmeldingKafkaMessage,
        oppfolgingsdato,
        forventaSoknader
    )
}
