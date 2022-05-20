package no.nav.helse.flex.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.logger
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class AutomatiskInnsendingVedDodsfallService(
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val automatiskInnsendingService: AutomatiskInnsendingService,
    private val registry: MeterRegistry
) {
    private val log = logger()

    fun sendSoknaderForDode(): Int {

        val aktorerMed2UkerGammelDodsmelding = dodsmeldingDAO.fnrMedToUkerGammelDodsmelding()

        val antall = aktorerMed2UkerGammelDodsmelding.size
        log.info("Fant $antall aktører med dødsfall eldre enn 2 uker")

        aktorerMed2UkerGammelDodsmelding.forEach { (fnr, dodsDato) ->
            try {
                MDC.put(NAV_CALLID, UUID.randomUUID().toString())

                val identer = automatiskInnsendingService.automatiskInnsending(fnr, dodsDato)

                dodsmeldingDAO.slettDodsmelding(identer)

                registry.counter("dodsmeldinger_prossesert").increment()
            } catch (e: Exception) {
                log.error("Feil ved prossering av dødsmelding for aktor $fnr", e)
            } finally {

                MDC.remove(NAV_CALLID)
            }
        }
        return antall
    }
}
