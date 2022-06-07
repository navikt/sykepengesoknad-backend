package no.nav.helse.flex.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.osloZone
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AktiverService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
    private val registry: MeterRegistry
) {
    val log = logger()

    fun aktiverSoknader(now: LocalDate = LocalDate.now(osloZone)): Int {
        log.info("Leter etter soknader som skal aktiveres")

        val soknaderSomSkalAktiveres = sykepengesoknadDAO.finnSoknaderSomSkalAktiveres(now)

        var i = 0
        soknaderSomSkalAktiveres.forEach {
            try {
                aktiverEnkeltSoknadService.aktiverSoknad(it)
                i++
                registry.counter("aktiverte_sykepengesoknader").increment()
            } catch (e: Exception) {
                log.error("Feilet ved aktivering av søknad med id $it", e)
            }
        }

        log.info("Aktivert $i soknader")
        return i
    }
}
