package no.nav.helse.flex.personhendelse

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.DodsmeldingDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.osloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class AutomatiskInnsendingJob(
    val automatiskInnsendingVedDodsfall: AutomatiskInnsendingVedDodsfall,
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val leaderElection: LeaderElection,
    val oppdaterSoknadOgDodsdato: OppdaterSoknadOgDodsdato,
    val env: EnvironmentToggles,
) {
    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun automatiskUtsending() {
        if (leaderElection.isLeader()) {
            if (env.isNotProduction() || LocalDate.now(osloZone).dayOfWeek == DayOfWeek.WEDNESDAY) {
                automatiskInnsendingVedDodsfall.sendSoknaderForDode()
            }
        }
    }

    @Scheduled(initialDelay = 5, fixedDelay = 86400, timeUnit = TimeUnit.MINUTES)
    fun settDodsdatoForSoknad() {
        if (leaderElection.isLeader() && env.isProduction()) {
            sykepengesoknadDAO.finnSykepengesoknad("4db2a376-6673-39b0-b27a-f68ce93744e6").let {
                if (it.status == Soknadstatus.UTGATT) {
                    oppdaterSoknadOgDodsdato.oppdaterSoknadOgLagreDodsdato(it)
                } else {
                    logger().info("Søknad: ${it.id} er ikke i status UTGATT, men ${it.status}")
                }
            }
        }
    }
}

// Egen Component sånn at klassen får en Spring-proxy og @Transactional fungerer.
@Component
class OppdaterSoknadOgDodsdato(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val dodsmeldingDao: DodsmeldingDAO,
    val identService: IdentService,
    val automatiskInnsendingVedDodsfall: AutomatiskInnsendingVedDodsfall,
) {
    private val log = logger()

    @Transactional
    fun oppdaterSoknadOgLagreDodsdato(sykepengesoknad: Sykepengesoknad) {
        sykepengesoknadDAO.oppdaterStatus(
            sykepengesoknad.copy(
                status = Soknadstatus.FREMTIDIG,
            ),
        )
        log.info("Oppdatert søknad ${sykepengesoknad.id} til status: FREMTIDIG.")

        val dodsdato = LocalDate.parse("2026-03-26")
        val meldingMottattDato = OffsetDateTime.now().minusMonths(1)
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(sykepengesoknad.fnr)
        dodsmeldingDao.lagreDodsmelding(
            identer = identer,
            dodsdato = dodsdato,
            // Jobben som sender søknader ved dødsfall sjekker etter meldinger som er eldre enn 2 uker,
            // så vi setter mottatt dato til 1 måned siden for å sikre at søknaden blir sendt.
            meldingMottattDato = meldingMottattDato,
        )

        try {
            dodsmeldingDao.fnrMedToUkerGammelDodsmelding().first { it.fnr == sykepengesoknad.fnr }
        } catch (e: Exception) {
            log.error(
                "Fant ikke dødsfall for fnr: ${sykepengesoknad.id}",
                e,
            )
        }
        log.info("Lagret dødsdato: $dodsdato og meldingMottattDato: $meldingMottattDato for soknad: ${sykepengesoknad.id}}")
        automatiskInnsendingVedDodsfall.automatiskInnsending(sykepengesoknad.fnr, dodsdato)
        dodsmeldingDao.slettDodsmelding(identer)
    }
}
