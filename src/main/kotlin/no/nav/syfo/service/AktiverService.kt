package no.nav.syfo.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AktiverService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
    private val registry: MeterRegistry
) {
    val log = logger()

    fun aktiverSoknader(now: LocalDate = LocalDate.now()): Int {
        log.info("Leter etter soknader som skal aktiveres")

        val soknaderSomSkalAktiveres = sykepengesoknadDAO.finnSoknaderSomSkalAktiveres(now)

        log.info("Fant ${soknaderSomSkalAktiveres.size} soknader som skal aktiveres")

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
