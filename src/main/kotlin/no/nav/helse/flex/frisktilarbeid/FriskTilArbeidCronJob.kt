package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

const val BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK = 50

@Component
class FriskTilArbeidCronJob(
    private val leaderElection: LeaderElection,
    private val friskTilArbeidService: FriskTilArbeidService,
    private val aktiveringProducer: AktiveringProducer,
) {
    private val log = logger()

    @Scheduled(initialDelay = 10, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun schedulertStartBehandlingAvFriskTilArbeidVedtakStatus() {
        if (leaderElection.isLeader()) {
            log.info("Er leder, starter behandling av FriskTilArbeidVedtakStatus.")
            behandleFriskTilArbeidVedtak()
        } else {
            log.info("Er ikke leder, kjører ikke behandling av FriskTilArbeidVedtakStatus.")
        }
    }

    fun behandleFriskTilArbeidVedtak() {
        friskTilArbeidService
            .behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
            .filter { it.tom!!.isBefore(LocalDate.now()) }
            .forEach {
                aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(it.fnr, it.id))
            }
    }
}
