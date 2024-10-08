package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(rollbackFor = [Throwable::class])
class EttersendingSoknadService(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val soknadProducer: SoknadProducer,
) {
    val log = logger()

    fun ettersendTilArbeidsgiver(sykepengesoknad: Sykepengesoknad) {
        if (sykepengesoknad.status != SENDT) {
            log.error(
                "Kan ikke ettersende søknad ${sykepengesoknad.id} med status: ${sykepengesoknad.status} til " +
                    "arbeidsgiver fordi den ikke er sendt",
            )
            throw IllegalArgumentException("Kan ikke ettersende søknad med status: ${sykepengesoknad.status} til arbeidsgiver")
        }
        if (sykepengesoknad.sendtArbeidsgiver != null) {
            log.info("Søknad ${sykepengesoknad.id} er allerede sendt til arbeidsgiver, ettersender ikke")
            return
        }

        fun kastFeil() {
            log.error("${sykepengesoknad.soknadstype} søknad: ${sykepengesoknad.id}  kan ikke ettersendes til arbeidsgiver.")
            throw IllegalArgumentException(
                "Søknad med id: ${sykepengesoknad.id} skal allerede ha blitt sendt til arbeidsgiber, men har ikke blitt det",
            )
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

    private fun Sykepengesoknad.ettersendArbeidsgiver() {
        sykepengesoknadDAO.settSendtAg(id, LocalDateTime.now())
        soknadProducer.soknadEvent(
            sykepengesoknadDAO.finnSykepengesoknad(id),
            Mottaker.ARBEIDSGIVER,
            true,
        )
    }
}
