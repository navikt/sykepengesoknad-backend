package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.Metrikk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(rollbackFor = [Throwable::class])
class AvbrytSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val metrikk: Metrikk,
    private val soknadProducer: SoknadProducer,
) {
    val log = logger()

    private fun Sykepengesoknad.avbrytOgPubliser() {
        sykepengesoknadDAO.avbrytSoknad(this, LocalDate.now())
        soknadProducer.soknadEvent(sykepengesoknadDAO.finnSykepengesoknad(this.id))
    }

    fun avbrytSoknad(sykepengesoknad: Sykepengesoknad) {
        when (sykepengesoknad.status) {
            Soknadstatus.UTKAST_TIL_KORRIGERING -> {
                sykepengesoknadDAO.slettSoknad(sykepengesoknad)
                metrikk.utkastTilKorrigeringAvbrutt()
            }
            Soknadstatus.NY, Soknadstatus.FREMTIDIG -> {
                metrikk.soknadAvbrutt(sykepengesoknad.soknadstype)
                return sykepengesoknad.avbrytOgPubliser()
            }
            else -> {
                log.error("Kan ikke avbryte søknad med status: {}", sykepengesoknad.status)
                throw IllegalArgumentException("Kan ikke avbryte søknad med status: " + sykepengesoknad.status)
            }
        }
    }
}
