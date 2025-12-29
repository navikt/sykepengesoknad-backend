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
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 4, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun resetVedtaksperiodeFomTom() {
        if (leaderElection.isLeader()) {
            val t1 = Triple("a76f6e88-4701-45fb-ab63-80874c15cf4f", LocalDate.of(2025, 11, 29), LocalDate.of(2026, 2, 21))
            val t2 = Triple("56fac704-37e0-4906-bb8b-566398604d56", LocalDate.of(2025, 12, 4), LocalDate.of(2026, 2, 26))

            listOf(t1, t2).forEach { tripple ->
                friskTilArbeidRepository.findById(tripple.first).get().also {
                    friskTilArbeidRepository.save(it.copy(fom = tripple.second, tom = tripple.third))
                    log.info("Endret FriskTilArbeid vedtak: ${it.id} til fom: ${tripple.second} og tom: ${tripple.third}")
                }
            }
        }
    }

    @Scheduled(initialDelay = 5, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
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
