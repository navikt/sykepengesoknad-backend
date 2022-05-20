package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.SykmeldingBehandletResultat
import no.nav.helse.flex.domain.exception.ManglerArbeidsgiverException
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class KlippOgOpprett(
    private val opprettSoknadService: OpprettSoknadService,
    private val soknadsklipper: Soknadsklipper,
) {
    val log = logger()

    fun klippOgOpprett(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        arbeidssituasjon: Arbeidssituasjon,
        identer: FolkeregisterIdenter,
        sykeForloep: List<Sykeforloep>
    ): SykmeldingBehandletResultat {
        var kafkaMessage = sykmeldingKafkaMessage
        val sykmeldingId = sykmeldingKafkaMessage.event.sykmeldingId

        val arbeidsgiverStatusDTO = sykmeldingKafkaMessage.event.arbeidsgiver
        if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && arbeidsgiverStatusDTO == null) {
            throw ManglerArbeidsgiverException("Arbeidsgiverstatus er null for sykmelding $sykmeldingId med arbeidssituasjon arbeidstaker")
        }

        kafkaMessage = soknadsklipper.klipp(
            sykmeldingKafkaMessage = kafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
            identer = identer,
        )

        return opprettSoknadService.opprettSykepengesoknaderForSykmelding(
            sykmeldingKafkaMessage = kafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
            arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
            flexSyketilfelleSykeforloep = sykeForloep,
        )
    }
}
