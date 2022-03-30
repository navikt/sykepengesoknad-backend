package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Sykeforloep
import no.nav.syfo.domain.SykmeldingBehandletResultat
import no.nav.syfo.domain.exception.ManglerArbeidsgiverException
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.logger
import no.nav.syfo.service.FolkeregisterIdenter
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

        val sykmeldingId = sykmeldingKafkaMessage.event.sykmeldingId

        val arbeidsgiverStatusDTO = sykmeldingKafkaMessage.event.arbeidsgiver
        if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && arbeidsgiverStatusDTO == null) {
            throw ManglerArbeidsgiverException("Arbeidsgiverstatus er null for sykmelding $sykmeldingId med arbeidssituasjon arbeidstaker")
        }

        soknadsklipper.klippEksisterendeSoknader(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
            identer = identer,
        )

        return opprettSoknadService.opprettSykepengesoknaderForSykmelding(
            sykmeldingKafkaMessage = sykmeldingKafkaMessage,
            arbeidssituasjon = arbeidssituasjon,
            identer = identer,
            arbeidsgiverStatusDTO = arbeidsgiverStatusDTO,
            flexSyketilfelleSykeforloep = sykeForloep,
        )
    }
}
