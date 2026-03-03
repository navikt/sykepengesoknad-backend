package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.exception.ManglerArbeidsgiverException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmelding.bestemSoknadsType
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(rollbackFor = [Throwable::class])
class Klipp(
    private val overlapp: Overlapp,
) {
    val log = logger()

    fun klippArbeidstaker(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
    ): SykmeldingKafkaMessage {
        val arbeidsgiverStatusDTO =
            sykmeldingKafkaMessage.event.arbeidsgiver
                ?: throw ManglerArbeidsgiverException(
                    "Arbeidsgiverstatus er null for sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId} med arbeidssituasjon arbeidstaker",
                )

        val soknadsType =
            bestemSoknadsType(
                arbeidssituasjon = arbeidssituasjon,
                perioderFraSykmeldingen = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder,
            )

        return if (soknadsType == Soknadstype.ARBEIDSTAKERE) {
            overlapp.klipp(
                sykmeldingKafkaMessage = sykmeldingKafkaMessage,
                arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
                identer = identer,
            )
        } else {
            sykmeldingKafkaMessage
        }
    }
}
