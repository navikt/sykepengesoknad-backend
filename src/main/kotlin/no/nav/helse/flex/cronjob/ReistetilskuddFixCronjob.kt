package no.nav.helse.flex.cronjob

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAtReisetilskudd
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class ReistetilskuddFixCronjob(
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val leaderElection: LeaderElection
) {

    val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun fixReisetilskddBugStarter() {
        if (leaderElection.isLeader()) {
            fixReistilskuddImpl()
        }
    }

    fun fixReistilskuddImpl(): Int {
        val kandidater = sykepengesoknadRepository.findBySoknadstypeAndAktivertDatoBetween(
            Soknadstype.REISETILSKUDD,
            LocalDate.of(2023, 11, 21),
            LocalDate.of(2023, 12, 9)
        )
        val bugSoknader = kandidater.filter {
            val soknad = sykepengesoknadDAO.finnSykepengesoknad(it.sykepengesoknadUuid)
            return@filter soknad.harBugCaset()
        }

        log.info("Fant ${bugSoknader.size} bug soknader")

        bugSoknader.map { it.sykepengesoknadUuid }.forEach { sykepengesoknadUuid ->
            log.info("Fikser vær klar over at bug for soknad $sykepengesoknadUuid")

            val soknad = sykepengesoknadDAO.finnSykepengesoknad(sykepengesoknadUuid)

            val oppdatertSoknad = soknad.copy(
                sporsmal = soknad.sporsmal.toMutableList().also { it.add(vaerKlarOverAtReisetilskudd()) }
            )
            sykepengesoknadDAO.byttUtSporsmal(oppdatertSoknad)
        }

        return bugSoknader.size
    }
}

fun Sykepengesoknad.harBugCaset(): Boolean {
    val harBekreftelse = this.sporsmal.any { it.tag == "BEKREFT_OPPLYSNINGER" }
    val harIkkeVaerKlarOVer = this.sporsmal.none { it.tag == "VAER_KLAR_OVER_AT" }
    return harBekreftelse && harIkkeVaerKlarOVer
}
