package no.nav.helse.flex.outbox

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import java.time.OffsetDateTime

interface OutboxService {
    fun outboxSykepengesoknad(sykepengesoknadDTO: SykepengesoknadDTO)

    fun outboxJuridiskVurdering(juridiskVurdering: JuridiskVurdering)

    fun outboxAktivering(aktiveringBestilling: AktiveringBestilling)

    fun outboxRebehandlingSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        behandlingstidspunkt: OffsetDateTime,
    )

    fun outboxAuditEntry(auditEntry: AuditEntry)

    fun sendUsendteForEldsteLedigeFnr(): Boolean
}

data class RebehandlingSykmeldingPayload(
    val sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    val behandlingstidspunkt: OffsetDateTime,
)
