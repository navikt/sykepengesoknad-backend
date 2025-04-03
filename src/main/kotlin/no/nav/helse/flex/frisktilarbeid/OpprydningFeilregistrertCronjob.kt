package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class OpprydningFeilregistrertCronjob(
    private val leaderElection: LeaderElection,
    private val arbeidssokerregisterStoppService: ArbeidssokerregisterStoppService,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Er leder, starter opprydning av FriskTilArbeidVedtakStatus.")
            opprydning()
        } else {
            log.info("Er ikke leder, kjører ikke opprydning av FriskTilArbeidVedtakStatus.")
        }
    }

    fun opprydning() {
        val id = "2f1ce3bc-5df3-4f9c-bed3-11878cc83c1a"

        val vedtakDbRecordOptional = friskTilArbeidRepository.findById(id)
        if (vedtakDbRecordOptional.isEmpty) {
            log.info("Ingen vedtak med id: $id funnet.")
            return
        }
        val vedtakDbRecord = vedtakDbRecordOptional.get()
        if (vedtakDbRecord.avsluttetTidspunkt != null) {
            log.info("Vedtak med id: $id er allerede avsluttet.")
            return
        }

        arbeidssokerregisterStoppService.prosseserStoppMelding(
            ArbeidssokerperiodeStoppMelding(
                vedtaksperiodeId = id,
                fnr = vedtakDbRecord.fnr,
                avsluttetTidspunkt = Instant.now(),
            ),
        )
    }
}
