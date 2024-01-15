@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class SykeforlopFixJob(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val leaderElection: LeaderElection,
) {
    private val log = logger()

    data class Soknad(
        val sykepengesoknad_uuid: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val startSykeforlop: LocalDate,
        val faktiskStartSykeforlop: LocalDate,
    )

    private val soknader =
        listOf(
            Soknad(
                sykepengesoknad_uuid = "74bf1ba3-85ad-3d0d-8679-3a2bc121edf7",
                fom = LocalDate.of(2023, 11, 27),
                tom = LocalDate.of(2023, 12, 1),
                startSykeforlop = LocalDate.of(2000, 6, 17),
                faktiskStartSykeforlop = LocalDate.of(2023, 11, 27),
            ),
            Soknad(
                sykepengesoknad_uuid = "bd1632d5-030a-3562-b683-02280b101102",
                fom = LocalDate.of(2023, 12, 5),
                tom = LocalDate.of(2023, 12, 18),
                startSykeforlop = LocalDate.of(2000, 6, 17),
                faktiskStartSykeforlop = LocalDate.of(2023, 11, 27),
            ),
            Soknad(
                "49ba6dde-0847-33e4-ae56-665d03e79b8f",
                fom = LocalDate.of(2023, 12, 19),
                tom = LocalDate.of(2023, 12, 31),
                startSykeforlop = LocalDate.of(2000, 6, 17),
                faktiskStartSykeforlop = LocalDate.of(2023, 11, 27),
            ),
            Soknad(
                sykepengesoknad_uuid = "030360c1-3146-33a9-9c16-93f3d57a6d87",
                fom = LocalDate.of(2023, 12, 5),
                tom = LocalDate.of(2023, 12, 18),
                startSykeforlop = LocalDate.of(2000, 6, 17),
                faktiskStartSykeforlop = LocalDate.of(2023, 12, 5),
            ),
        )

    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun job() {
        if (leaderElection.isLeader()) {
            fix(soknader)
        }
    }

    fun fix(soknader: List<Soknad>) {
        soknader.forEach { sok ->
            log.info("Henter soknad ${sok.sykepengesoknad_uuid}")
            val soknaden = sykepengesoknadDAO.finnSykepengesoknad(sok.sykepengesoknad_uuid)
            require(soknaden.id == sok.sykepengesoknad_uuid)
            require(soknaden.fom == sok.fom)
            require(soknaden.tom == sok.tom)

            // Oppdaterer bare en gang
            if (soknaden.startSykeforlop == sok.startSykeforlop) {
                log.info(
                    "Flytter soknad ${sok.sykepengesoknad_uuid} sin startSykeforlop fra ${soknaden.startSykeforlop} til ${sok.faktiskStartSykeforlop}",
                )
                sykepengesoknadRepository.oppdaterStartSykeforlop(sok.faktiskStartSykeforlop, sok.sykepengesoknad_uuid)
            }
        }
    }
}
