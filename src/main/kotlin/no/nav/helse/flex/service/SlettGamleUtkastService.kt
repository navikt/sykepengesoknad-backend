package no.nav.helse.flex.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.syfo.logger
import org.springframework.stereotype.Service

@Service
class SlettGamleUtkastService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadDAOKt: SykepengesoknadDAO,
    private val registry: MeterRegistry

) {
    val log = logger()

    fun slettGamleUtkast(): Int {
        log.info("Leter etter gamle utkast som skal slettes")

        val utkastForSletting = sykepengesoknadDAOKt.finnGamleUtkastForSletting()
        val antall = utkastForSletting.size
        log.info("Fant $antall utkast som kan slettes")

        utkastForSletting.forEach {
            sykepengesoknadDAO.slettSoknad(it.sykepengesoknadUuid)
            registry.counter("utkast_slettet").increment()
        }
        log.info("Ferdig med sletting av $antall utkast")

        return antall
    }
}
