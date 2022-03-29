package no.nav.syfo.service

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.Metrikk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class EttersendingSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val soknadProducer: SoknadProducer,
    val metrikk: Metrikk
) {
    val log = logger()

    fun ettersendTilArbeidsgiver(sykepengesoknad: Sykepengesoknad) {
        if (sykepengesoknad.status != SENDT) {
            log.error("Kan ikke ettersende søknad ${sykepengesoknad.id} med status: ${sykepengesoknad.status} til arbeidsgiver fordi den ikke er sendt")
            throw IllegalArgumentException("Kan ikke ettersende søknad med status: ${sykepengesoknad.status} til arbeidsgiver")
        }
        if (sykepengesoknad.sendtArbeidsgiver != null) {
            log.info("Søknad ${sykepengesoknad.id} er allerede sendt til arbeidsgiver, ettersender ikke")
            return
        }

        fun kastFeil() {
            log.error("${sykepengesoknad.soknadstype} søknad: ${sykepengesoknad.id}  kan ikke ettersendes til arbeidsgiver.")
            throw IllegalArgumentException("Søknad med id: ${sykepengesoknad.id} skal allerede ha blitt sendt til arbeidsgiber, men har ikke blitt det")
        }

        when (sykepengesoknad.soknadstype) {
            Soknadstype.ARBEIDSTAKERE -> {
                sykepengesoknad.ettersendArbeidsgiver()
            }
            Soknadstype.BEHANDLINGSDAGER, Soknadstype.GRADERT_REISETILSKUDD -> {
                if (sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                    sykepengesoknad.ettersendArbeidsgiver()
                } else {
                    kastFeil()
                }
            }
            else -> {
                kastFeil()
            }
        }
    }

    fun ettersendTilNav(sykepengesoknad: Sykepengesoknad) {
        if (sykepengesoknad.status != SENDT) {
            log.error("Kan ikke ettersende søknad ${sykepengesoknad.id} med status: ${sykepengesoknad.status} til NAV fordi den ikke er sendt")
            throw IllegalArgumentException("Kan ikke ettersende søknad med status: ${sykepengesoknad.status} til NAV")
        }

        if (sykepengesoknad.sendtNav != null) {
            log.info("Søknad ${sykepengesoknad.id} er allerede sendt til NAV, ettersender ikke")
            return
        }

        fun kastFeil() {
            log.error("${sykepengesoknad.soknadstype} søknad: ${sykepengesoknad.id}  har ikke arbeidsgiver, og er ikke sendt til NAV.")
            throw IllegalArgumentException("Søknad med id: ${sykepengesoknad.id} er ikke arbeidstakersøknad og skal allerede ha blitt sendt til NAV, men har ikke blitt det")
        }

        when (sykepengesoknad.soknadstype) {
            Soknadstype.ARBEIDSTAKERE -> {
                sykepengesoknad.ettersendNav()
            }
            Soknadstype.BEHANDLINGSDAGER, Soknadstype.GRADERT_REISETILSKUDD -> {
                if (sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                    sykepengesoknad.ettersendNav()
                } else {
                    kastFeil()
                }
            }
            else -> {
                kastFeil()
            }
        }
    }

    private fun Sykepengesoknad.ettersendNav() {
        if (sendtArbeidsgiver == null) {
            log.error("Søknad $id må være sendt til arbeidsgiver før den kan ettersendes til NAV")
            throw IllegalArgumentException("Søknad med id: $id må være sendt til arbeidsgiver før den kan ettersendes til NAV")
        }
        sykepengesoknadDAO.settSendtNav(id, LocalDateTime.now())
        soknadProducer.soknadEvent(
            sykepengesoknadDAO.finnSykepengesoknad(id),
            Mottaker.ARBEIDSGIVER_OG_NAV,
            true
        )
        metrikk.ettersending(Mottaker.NAV.name)
    }

    private fun Sykepengesoknad.ettersendArbeidsgiver() {
        sykepengesoknadDAO.settSendtAg(id, LocalDateTime.now())
        soknadProducer.soknadEvent(
            sykepengesoknadDAO.finnSykepengesoknad(id),
            Mottaker.ARBEIDSGIVER,
            true
        )
        metrikk.ettersending(Mottaker.ARBEIDSGIVER.name)
    }
}
