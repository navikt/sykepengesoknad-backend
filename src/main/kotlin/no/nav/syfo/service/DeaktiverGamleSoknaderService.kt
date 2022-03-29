package no.nav.syfo.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class DeaktiverGamleSoknaderService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val registry: MeterRegistry
) {
    val log = logger()

    fun deaktiverSoknader(): Int {
        log.info("Leter etter soknader som skal deaktiveres")

        val antall = sykepengesoknadDAO.deaktiverSoknader()
        log.info("Deaktivert $antall søknader")

        registry.counter("deaktiverte_soknader").increment(antall.toDouble())
        return antall
    }
}
