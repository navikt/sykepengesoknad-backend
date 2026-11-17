package no.nav.helse.flex.service

import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(rollbackFor = [Throwable::class])
class PubliserUtgaattSoknad(
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    fun publiser(soknadId: String) {
        soknadProducer.soknadEvent(sykepengesoknadDAO.finnSykepengesoknad(soknadId))
        sykepengesoknadDAO.settUtloptPublisert(soknadId, LocalDateTime.now())
    }
}
