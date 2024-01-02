package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GjenapneSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
) {
    fun gjenapneSoknad(sykepengesoknad: Sykepengesoknad) {
        sykepengesoknadDAO.gjenapneSoknad(sykepengesoknad)
        soknadProducer.soknadEvent(sykepengesoknadDAO.finnSykepengesoknad(sykepengesoknad.id), null, false, null)
    }
}
