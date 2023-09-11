package no.nav.helse.flex

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class OppdaterForstegangssoknadJob(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val leaderElection: LeaderElection
) {

    private val log = logger()

    var antallOppdatert = AtomicInteger(0)
    var antallFeilet = AtomicInteger(0)

    @Scheduled(initialDelay = 60 * 3, fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    fun oppdaterForstegangssoknadJob() {
        if (leaderElection.isLeader()) {
            log.info("er leader i job")
            val soknader = sykepengesoknadRepository.finnSoknaderUtenForstegangs(1000)

            if (soknader.isEmpty()) {
                log.info("Ingen søknader å oppdatere med forstegangssoknad")
                return
            }
            log.info("fant ${soknader.size} søknader å oppdatere med forstegangssoknad")

            soknader.map { it.fnr }.toSet().forEach { fnr ->
                try {
                    val soknaderForFnr = sykepengesoknadRepository.findByFnrIn(listOf(fnr))
                        .filter { it.soknadstype != Soknadstype.OPPHOLD_UTLAND }
                    soknaderForFnr.map { it.markerForsteganssoknad(soknaderForFnr) }
                        .filter { it.first.forstegangssoknad == null }
                        .forEach {
                            sykepengesoknadDAO.oppdaterMedForstegangssoknad(it.first.id!!, it.second)
                        }

                    antallOppdatert.incrementAndGet()
                } catch (e: RuntimeException) {
                    log.warn("Kunne ikke oppdatere med forstegangssoknad", e)
                    antallFeilet.incrementAndGet()
                }
            }
            if (antallOppdatert.toInt() % 100 == 0) {
                log.info("Oppdatert $antallOppdatert søknadder med forstegangssoknad. $antallFeilet feilet.")
            }
        }
    }
}

// All koden under her er kopiert ut, men omskrevet til å bruke SykepengesoknadDbRecord istedenfor Sykepengesoknad.
// Siden koden skal slettes er det ikke noe poeng å gjøre det på en pen måte.
private fun SykepengesoknadDbRecord.markerForsteganssoknad(
    alleSoknader: List<SykepengesoknadDbRecord>
): Pair<SykepengesoknadDbRecord, Boolean> {
    return Pair(
        this,
        erForsteSoknadTilArbeidsgiverIForlop(
            alleSoknader.filterNot { eksisterendeSoknad -> eksisterendeSoknad.id == this.id },
            this
        )
    )
}

fun erForsteSoknadTilArbeidsgiverIForlop(
    eksisterendeSoknader: List<SykepengesoknadDbRecord>,
    sykepengesoknad: SykepengesoknadDbRecord
): Boolean {
    return finnTidligereSoknaderMedSammeArbeidssituasjon(eksisterendeSoknader, sykepengesoknad)
        // Sjekker om det finnes en tidligere søknad med samme startdato for sykeforløp.
        .none { it.startSykeforlop == sykepengesoknad.startSykeforlop }
}

// Returnerer en liste med søknader med tidligere 'fom' og samme arbeidssituasjon som søknaden det sammenlignes med.
// Om det er arbeidssituasjon ARBEIDSTAKER med søknadstype BEHANDLINGSDAGER, GRADERT_REISETILSKUD eller ARBEIDSTAKER
// sjekkes det at arbeidsgiver er den samme.
private fun finnTidligereSoknaderMedSammeArbeidssituasjon(
    eksisterendeSoknader: List<SykepengesoknadDbRecord>,
    sykepengesoknad: SykepengesoknadDbRecord
): Sequence<SykepengesoknadDbRecord> {
    return eksisterendeSoknader
        .asSequence()
        .filter { it.fom != null && it.fom.isBefore(sykepengesoknad.fom) }
        .filter { it.sykmeldingUuid != null && it.startSykeforlop != null }
        .filter { it.arbeidssituasjon == sykepengesoknad.arbeidssituasjon }
        .filter {
            if (soknadHarArbeidsgiver(sykepengesoknad)) {
                if (listOf(
                        Soknadstype.ARBEIDSTAKERE,
                        Soknadstype.BEHANDLINGSDAGER,
                        Soknadstype.GRADERT_REISETILSKUDD
                    ).contains(it.soknadstype)
                ) {
                    return@filter it.arbeidsgiverOrgnummer == sykepengesoknad.arbeidsgiverOrgnummer
                }
            }
            true
        }
}

private fun soknadHarArbeidsgiver(sykepengesoknad: SykepengesoknadDbRecord) =
    sykepengesoknad.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER && sykepengesoknad.arbeidsgiverOrgnummer != null
