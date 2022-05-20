package no.nav.syfo.service

import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GjenapneSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer
) {
    fun gjenapneSoknad(sykepengesoknad: Sykepengesoknad) {
        sykepengesoknadDAO.gjenapneSoknad(sykepengesoknad)
        soknadProducer.soknadEvent(sykepengesoknadDAO.finnSykepengesoknad(sykepengesoknad.id), null, false, null)
    }
}
