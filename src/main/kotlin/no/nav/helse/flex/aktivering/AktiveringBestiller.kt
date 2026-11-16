package no.nav.helse.flex.aktivering

import no.nav.helse.flex.outbox.OutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(rollbackFor = [Throwable::class])
class AktiveringBestiller(
    private val outboxService: OutboxService,
) {
    fun bestillAktivering(aktiveringBestilling: AktiveringBestilling) {
        outboxService.outboxAktivering(aktiveringBestilling)
    }
}
