package no.nav.helse.flex.personhendelse

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.util.osloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class FiksDodsfall(
    val leaderElection: LeaderElection,
    private val dodsmeldingDAO: DodsmeldingDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository

) {

    val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    fun fiksDodsfall() {
        if (leaderElection.isLeader()) {
            val soknad =
                sykepengesoknadRepository.findBySykepengesoknadUuid("a6b38fb8-3efc-3609-a242-0b2a1837a3e3")
            if (soknad == null) {
                log.info("Fant ikke søknad")
                return
            }

            val fnr = soknad.fnr
            val identer = FolkeregisterIdenter(fnr, emptyList())
            val harDodsmelding = dodsmeldingDAO.harDodsmelding(identer)

            if (harDodsmelding) {
                log.info("Søknad med uuid ${soknad.sykepengesoknadUuid} har dødsfall")
                return
            }

            dodsmeldingDAO.lagreDodsmelding(
                identer,
                LocalDate.of(2022, 11, 17),
                OffsetDateTime.now(osloZone).minusWeeks(3)
            )
        }
    }
}
