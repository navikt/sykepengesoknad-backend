package no.nav.helse.flex.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class SlettGamleUtkastService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadDAOKt: SykepengesoknadDAO,
) {
    val log = logger()

    fun slettGamleUtkast(): Int {
        log.info("Leter etter gamle utkast som skal slettes")

        val utkastForSletting = sykepengesoknadDAOKt.finnGamleUtkastForSletting()
        var antall = 0

        utkastForSletting.forEach {
            sykepengesoknadDAO.slettSoknad(it.sykepengesoknadUuid)
            antall++
            log.info("Slettet utkast ${it.sykepengesoknadUuid} ")
        }

        log.info("Ferdig med sletting av $antall utkast")
        return antall
    }
}
