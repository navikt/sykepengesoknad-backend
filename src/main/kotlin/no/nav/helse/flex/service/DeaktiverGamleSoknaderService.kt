package no.nav.helse.flex.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class DeaktiverGamleSoknaderService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val registry: MeterRegistry,
) {
    val log = logger()

    fun deaktiverSoknader(): Int {
        log.info("Leter etter soknader som skal deaktiveres")

        val soknaderSomSkalDeaktiveres = sykepengesoknadDAO.finnSoknaderSomSkalDeaktiveres()
        soknaderSomSkalDeaktiveres.forEach {
            sykepengesoknadDAO.slettAlleSvar(it)
        }
        log.info("Slettet ${soknaderSomSkalDeaktiveres.size} fra avbrutte søknader som skal deaktiveres")

        val antall = sykepengesoknadDAO.deaktiverSoknader()
        log.info("Deaktivert $antall søknader")

        registry.counter("deaktiverte_soknader").increment(antall.toDouble())
        return antall
    }
}
