package no.nav.helse.flex.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class DeaktiverGamleSoknaderService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    fun deaktiverSoknader(): Int {
        log.info("Leter etter soknader som skal deaktiveres")

        val antall = sykepengesoknadDAO.deaktiverSoknader()
        log.info("Deaktivert $antall søknader")
        return antall
    }
}
