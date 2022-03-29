package no.nav.syfo.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.logger
import no.nav.syfo.repository.DodsmeldingDAO
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.util.*
import javax.transaction.Transactional

@Service
@Transactional
class AutomatiskInnsendingVedDodsfallService(
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val automatiskInnsendingService: AutomatiskInnsendingService,
    private val registry: MeterRegistry
) {
    private val log = logger()

    fun sendSoknaderForDode(): Int {

        val aktorerMed2UkerGammelDodsmelding = dodsmeldingDAO.aktorIderMedToUkerGammelDodsmelding()

        val antall = aktorerMed2UkerGammelDodsmelding.size
        log.info("Fant $antall aktører med dødsfall eldre enn 2 uker")

        aktorerMed2UkerGammelDodsmelding.forEach { (aktorId, dodsDato) ->
            try {
                MDC.put(NAV_CALLID, UUID.randomUUID().toString())

                automatiskInnsendingService.automatiskInnsending(aktorId, dodsDato)

                dodsmeldingDAO.slettDodsmelding(aktorId)

                registry.counter("dodsmeldinger_prossesert").increment()
            } catch (e: Exception) {
                log.error("Feil ved prossering av dødsmelding for aktor $aktorId", e)
            } finally {

                MDC.remove(NAV_CALLID)
            }
        }
        return antall
    }
}
