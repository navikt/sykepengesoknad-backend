package no.nav.helse.flex.fakes

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.outbox.OutboxService
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * Sender direkte på Kafka i stedet for å gå via outbox-tabellen, siden FakesTestOppsett ikke har database.
 * Integrasjonstestene i FellesTestOppsett bruker den ekte outboxen via PausbarOutboxService.
 */
@Service
@Primary
@Profile("fakes")
class OutboxServiceFake(
    private val aivenKafkaProducer: AivenKafkaProducer,
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val aktiveringProducer: AktiveringProducer,
    private val rebehandlingSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer,
    private val auditLogProducer: AuditLogProducer,
) : OutboxService {
    override fun outboxSykepengesoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        aivenKafkaProducer.produserMelding(sykepengesoknadDTO)
    }

    override fun outboxJuridiskVurdering(juridiskVurdering: JuridiskVurdering) {
        juridiskVurderingKafkaProducer.produserMelding(juridiskVurderingKafkaProducer.tilDto(juridiskVurdering))
    }

    override fun outboxAktivering(aktiveringBestilling: AktiveringBestilling) {
        aktiveringProducer.leggPaAktiveringTopic(aktiveringBestilling)
    }

    override fun outboxRebehandlingSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        behandlingstidspunkt: OffsetDateTime,
    ) {
        rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(sykmeldingKafkaMessage, behandlingstidspunkt)
    }

    override fun outboxAuditEntry(auditEntry: AuditEntry) {
        auditLogProducer.lagAuditLog(auditEntry)
    }

    override fun sendUsendteForEldsteLedigeFnr(): Boolean = false
}
