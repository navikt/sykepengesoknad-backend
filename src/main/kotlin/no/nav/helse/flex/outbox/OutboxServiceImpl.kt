package no.nav.helse.flex.outbox

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaDto
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.fraPostgresJson
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OutboxServiceImpl(
    private val outboxDbRepository: OutboxDbRepository,
    private val aivenKafkaProducer: AivenKafkaProducer,
    private val juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer,
    private val aktiveringProducer: AktiveringProducer,
    private val rebehandlingSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer,
    private val auditLogProducer: AuditLogProducer,
) : OutboxService {
    private val log = logger()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun outboxSykepengesoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        lagre(OutboxType.SYKEPENGESOKNAD, sykepengesoknadDTO.fnr, sykepengesoknadDTO)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun outboxJuridiskVurdering(juridiskVurdering: JuridiskVurdering) {
        val dto = juridiskVurderingKafkaProducer.tilDto(juridiskVurdering)
        lagre(OutboxType.JURIDISK_VURDERING, dto.fodselsnummer, dto)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun outboxAktivering(aktiveringBestilling: AktiveringBestilling) {
        lagre(OutboxType.AKTIVERING, aktiveringBestilling.fnr, aktiveringBestilling)
    }

    // Egen transaksjon fordi BehandleSendtBekreftetSykmelding ruller tilbake sin transaksjon etter at rebehandling er bestilt.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun outboxRebehandlingSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        behandlingstidspunkt: OffsetDateTime,
    ) {
        lagre(
            OutboxType.REBEHANDLING_SYKMELDING,
            sykmeldingKafkaMessage.kafkaMetadata.fnr,
            RebehandlingSykmeldingPayload(sykmeldingKafkaMessage, behandlingstidspunkt),
        )
    }

    // Audit trenger ikke rekkefølge per person, og skal ikke blokkeres av andre rader for samme person.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun outboxAuditEntry(auditEntry: AuditEntry) {
        lagre(OutboxType.AUDIT, "audit-${UUID.randomUUID()}", auditEntry)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun sendEldsteUsendte(): Boolean {
        val record = outboxDbRepository.finnEldsteLedigeRadOgLas() ?: return false
        send(record)
        outboxDbRepository.save(record.copy(sendtTimestamp = Instant.now()))
        return true
    }

    private fun lagre(
        type: OutboxType,
        fnr: String,
        payload: Any,
    ) {
        outboxDbRepository.save(
            OutboxDbRecord(
                type = type,
                fnr = fnr,
                payload = payload.tilPostgresJson(),
                opprettetTimestamp = Instant.now(),
                sendtTimestamp = null,
            ),
        )
    }

    private fun send(record: OutboxDbRecord) {
        when (record.type) {
            OutboxType.SYKEPENGESOKNAD -> {
                val soknad = record.payload.fraPostgresJson<SykepengesoknadDTO>()
                aivenKafkaProducer.produserMelding(soknad)
                log.info("Sendte outbox-rad ${record.type} ${record.id} for søknad ${soknad.id}")
            }

            OutboxType.JURIDISK_VURDERING -> {
                val vurdering = record.payload.fraPostgresJson<JuridiskVurderingKafkaDto>()
                juridiskVurderingKafkaProducer.produserMelding(vurdering)
                log.info("Sendte outbox-rad ${record.type} ${record.id} for juridisk vurdering ${vurdering.id}")
            }

            OutboxType.AKTIVERING -> {
                val bestilling = record.payload.fraPostgresJson<AktiveringBestilling>()
                aktiveringProducer.leggPaAktiveringTopic(bestilling)
                log.info("Sendte outbox-rad ${record.type} ${record.id} for søknad ${bestilling.soknadId}")
            }

            OutboxType.REBEHANDLING_SYKMELDING -> {
                val payload = record.payload.fraPostgresJson<RebehandlingSykmeldingPayload>()
                rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(
                    payload.sykmeldingKafkaMessage,
                    payload.behandlingstidspunkt,
                )
                log.info(
                    "Sendte outbox-rad ${record.type} ${record.id} for sykmelding ${payload.sykmeldingKafkaMessage.event.sykmeldingId}",
                )
            }

            OutboxType.AUDIT -> {
                val auditEntry = record.payload.fraPostgresJson<AuditEntry>()
                auditLogProducer.lagAuditLog(auditEntry)
                log.info("Sendte outbox-rad ${record.type} ${record.id} for ${auditEntry.eventType} fra ${auditEntry.appNavn}")
            }
        }
    }
}
