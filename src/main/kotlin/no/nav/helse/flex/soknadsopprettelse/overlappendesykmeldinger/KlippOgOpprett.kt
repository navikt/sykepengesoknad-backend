package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.exception.ManglerArbeidsgiverException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmelding.finnSoknadsType
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(rollbackFor = [Throwable::class])
class KlippOgOpprett(
    private val opprettSoknadService: OpprettSoknadService,
    private val overlapp: Overlapp,
) {
    val log = logger()

    fun klippOgOpprett(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        sykeForloep: List<Sykeforloep>,
    ): List<AktiveringBestilling> {
        var kafkaMessage = sykmeldingKafkaMessage
        val sykmeldingId = sykmeldingKafkaMessage.event.sykmeldingId

        val arbeidsgiverStatusDTO = sykmeldingKafkaMessage.event.arbeidsgiver
        if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && arbeidsgiverStatusDTO == null) {
            throw ManglerArbeidsgiverException("Arbeidsgiverstatus er null for sykmelding $sykmeldingId med arbeidssituasjon arbeidstaker")
        }

        if (finnSoknadsType(
                arbeidssituasjon = arbeidssituasjon,
                perioderFraSykmeldingen = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder,
            ) == Soknadstype.ARBEIDSTAKERE
        ) {
            kafkaMessage =
                overlapp.klipp(
                    sykmeldingKafkaMessage = kafkaMessage,
                    arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
                    identer = identer,
                )
        }

        return opprettSoknadService.opprettSykepengesoknaderForSykmelding(
            sykmeldingKafkaMessage = kafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
            arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
            flexSyketilfelleSykeforloep = sykeForloep,
        )
    }
}
