package no.nav.helse.flex.service

import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.outbox.OutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(rollbackFor = [Throwable::class])
class AuditLogger(
    private val outboxService: OutboxService,
) {
    fun lagAuditLog(auditEntry: AuditEntry) {
        outboxService.outboxAuditEntry(auditEntry)
    }
}
