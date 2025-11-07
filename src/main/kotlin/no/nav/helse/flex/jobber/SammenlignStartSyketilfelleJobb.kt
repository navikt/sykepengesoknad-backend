package no.nav.helse.flex.jobber

import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.IdentService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class SammenlignStartSyketilfelleJobb(
    val leaderElection: LeaderElection,
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val identService: IdentService,
    val syketilfelleClient: FlexSyketilfelleClient,
) {
    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun startSammenlignStartSyketilfelleJobb() {
        log.info("Kjører scheduled jobb SammenlignStartSyketilfelleJobb.")
        if (leaderElection.isLeader()) {
            log.info("Er leader starter jobb SammenlignStartSyketilfelleJobb.")
            sammenlign()
        } else {
            log.info("Er ikke leader, starter ikke jobb SammenlignStartSyketilfelleJobb.")
        }
    }

    fun sammenlign() {
        val soknaderMedForskjelligStartSykeforlop = mutableListOf<String>()
        MULIG_BERORTE_SOKNADER_ID.forEach { berortSoknadId ->
            val soknad = sykepengesoknadRepository.findBySykepengesoknadUuid(berortSoknadId)!!
            val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
            val nyttSykeforloep = syketilfelleClient.hentSykeforloepUtenKafkaMessage(identer)
            val nyttStartSykeforlop =
                nyttSykeforloep
                    .firstOrNull {
                        it.sykmeldinger.any { sm -> sm.id == soknad.sykmeldingUuid }
                    }!!
                    .oppfolgingsdato

            if (!soknad.startSykeforlop!!.isEqual(nyttStartSykeforlop)) {
                soknaderMedForskjelligStartSykeforlop.add(soknad.id!!)
            }
        }

        log.info(
            "Antall søknader med forskjellig startsykeforlop ${soknaderMedForskjelligStartSykeforlop.size} av ${MULIG_BERORTE_SOKNADER_ID.size}. Søknad IDer: $soknaderMedForskjelligStartSykeforlop",
        )
    }
}

val MULIG_BERORTE_SOKNADER_ID =
    listOf(
        "d849cea6-0421-38a9-9657-bac4225bf499",
        "9cecc85c-009a-48a6-b0d5-00a0afa6a1ac",
        "e8dba52d-8dc8-4e7b-9c12-7d318580c513",
        "94991a98-9570-4e33-ba61-79ef8f5f139c",
        "1d7d1e17-fc8b-3a53-b3d3-f97f85b20d53",
        "dc056430-11c8-330a-9d2c-9563f6832bdd",
        "e02cf31f-27c0-33d5-9f43-3350b3b5f162",
        "346c4e14-24bd-3511-a0c7-ffc51d0b0df6",
        "5f6333f9-da28-33ab-bafe-dce3ad41b84c",
        "3918df63-d4c3-3cca-a5e8-7d5cbd3dc75c",
        "43be9875-20f3-3696-bf0d-68649d7abbc0",
        "0459de06-61b4-3db4-a13a-da75d9b29a76",
    )
