package no.nav.helse.flex.jobber

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OppdaterMerknaderJobb(
    val leaderElection: LeaderElection,
    val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun startSammenlignStartSyketilfelleJobb() {
        log.info("Kjører scheduled jobb OppdaterMerknaderJobb.")
        if (leaderElection.isLeader()) {
            log.info("Er leader starter jobb OppdaterMerknaderJobb.")
            oppdaterMerknader()
        } else {
            log.info("Er ikke leader, starter ikke jobb OppdaterMerknaderJobb.")
        }
    }

    fun oppdaterMerknader() {
        MULIG_BERORTE_SYKMELDINGER.forEach { id ->
            val soknader = sykepengesoknadRepository.findBySykmeldingUuid(id)
            if (soknader.isEmpty()) {
                log.info("Fant ikke sykepengesoknad med sykmeldingId $id, hopper over")
                return@forEach
            }

            log.info("Oppdaterer merknader for ${soknader.size} sykepengesoknader med sykmeldingId $id")
            soknader.forEach { soknad ->
                if (soknad.merknaderFraSykmelding == "[]") {
                    sykepengesoknadRepository.save(soknad.copy(merknaderFraSykmelding = null))
                    log.info("Oppdaterer merknader for sykepengesoknad med id ${soknad.sykepengesoknadUuid} til null")
                } else {
                    log.info("Merknader for sykepengesoknad med id ${soknad.sykepengesoknadUuid} er allerede oppdatert")
                }
            }
        }
    }
}

val MULIG_BERORTE_SYKMELDINGER =
    listOf(
        "69b88c45-dd4b-4246-8fa2-bc17acb8997b",
        "8b0b8414-a938-46b3-86c1-a3324d6ceffa",
        "dcb6de66-44d4-4e7e-be06-be928b72b94d",
        "68bd0410-60f4-4ae8-8a94-85c88bbd130a",
        "3bb35024-e801-4650-8110-5a1ca6d8cd9b",
        "225f8327-3d92-4402-85af-c5983e44b717",
        "0cd8d689-a095-4e33-8eb3-c21e74f12557",
        "86596ab8-5765-4596-aff9-dca5b56af506",
        "e475ca78-597f-4f31-b407-d915fb5cdaf2",
        "fcecd546-6454-4168-8e82-798a6298c094",
        "e79755f8-297e-4c22-bcf5-bd097afe813e",
        "9f0f5ed8-6339-4be0-ae28-663051c109f7",
        "f1614acb-7a35-4a0c-8f71-49ef65733d05",
        "bb306406-6dbc-4f7f-8ab8-2211d53c9459",
        "5b94ea18-3052-4cd1-8946-fd1a77e7fe2e",
        "ab651f6c-615c-48c5-85ea-8e9fab8be664",
    )
