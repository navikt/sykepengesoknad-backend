package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.LockRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ProsseserKafkaMeldingFraSpleiselaget(
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
    private val lockRepository: LockRepository,
) {
    val log = logger()

    @Transactional
    fun prosesserKafkaMelding(kafkaDto: Behandlingstatusmelding) {
        lockRepository.settAdvisoryTransactionLock(kafkaDto.vedtaksperiodeId)

        val vedtaksperiodeBehandling =
            vedtaksperiodeBehandlingRepository.findByVedtaksperiodeIdAndBehandlingId(
                vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                behandlingId = kafkaDto.behandlingId,
            )

        fun lagreSøknadIder(vedtaksperiodeBehandlingDbRecord: VedtaksperiodeBehandlingDbRecord) {
            kafkaDto.eksterneSøknadIder.forEach { eksternSøknadId ->

                val eksternSøknadForDenneBehandlingenMangler =
                    vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(
                        listOf(eksternSøknadId),
                    ).none { it.vedtaksperiodeBehandlingId == vedtaksperiodeBehandlingDbRecord.id }

                if (eksternSøknadForDenneBehandlingenMangler) {
                    vedtaksperiodeBehandlingSykepengesoknadRepository.save(
                        VedtaksperiodeBehandlingSykepengesoknadDbRecord(
                            vedtaksperiodeBehandlingId = vedtaksperiodeBehandlingDbRecord.id!!,
                            sykepengesoknadUuid = eksternSøknadId,
                        ),
                    )
                }
            }
        }
        if (vedtaksperiodeBehandling == null) {
            if (kafkaDto.status == Behandlingstatustype.OPPRETTET) {
                val vedtaksperiodeBehandlingDbRecord =
                    vedtaksperiodeBehandlingRepository.save(
                        VedtaksperiodeBehandlingDbRecord(
                            behandlingId = kafkaDto.behandlingId,
                            vedtaksperiodeId = kafkaDto.vedtaksperiodeId,
                            opprettetDatabase = Instant.now(),
                            oppdatertDatabase = Instant.now(),
                            sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                            sisteSpleisstatusTidspunkt = kafkaDto.tidspunkt.toInstant(),
                        ),
                    )

                lagreSøknadIder(vedtaksperiodeBehandlingDbRecord)

                vedtaksperiodeBehandlingStatusRepository.save(
                    VedtaksperiodeBehandlingStatusDbRecord(
                        vedtaksperiodeBehandlingId = vedtaksperiodeBehandlingDbRecord.id!!,
                        opprettetDatabase = Instant.now(),
                        tidspunkt = kafkaDto.tidspunkt.toInstant(),
                        status = kafkaDto.status.tilStatusVerdi(),
                    ),
                )

                return
            }

            log.info(
                "Fant ikke vedtaksperiodeBehandling for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} " +
                    "og behandlingId ${kafkaDto.behandlingId}. Det kan skje for perioder opprettet før mai 2024",
            )
            return
        }

        lockRepository.settAdvisoryTransactionLock(kafkaDto.vedtaksperiodeId)

        lagreSøknadIder(vedtaksperiodeBehandling)

        if (kafkaDto.status == Behandlingstatustype.OPPRETTET) {
            log.warn(
                "Skal ikke motta status OPPRETTET for vedtaksperiodeId ${kafkaDto.vedtaksperiodeId} Den skal allerede være opprettet",
            )
            return
        }

        vedtaksperiodeBehandlingStatusRepository.save(
            VedtaksperiodeBehandlingStatusDbRecord(
                vedtaksperiodeBehandlingId = vedtaksperiodeBehandling.id!!,
                opprettetDatabase = Instant.now(),
                tidspunkt = kafkaDto.tidspunkt.toInstant(),
                status = kafkaDto.status.tilStatusVerdi(),
            ),
        )
        vedtaksperiodeBehandlingRepository.save(
            vedtaksperiodeBehandling.copy(
                sisteSpleisstatus = kafkaDto.status.tilStatusVerdi(),
                sisteSpleisstatusTidspunkt = kafkaDto.tidspunkt.toInstant(),
                oppdatertDatabase = Instant.now(),
            ),
        )
    }
}
