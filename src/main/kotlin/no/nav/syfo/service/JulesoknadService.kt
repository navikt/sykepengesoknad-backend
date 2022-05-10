package no.nav.syfo.service

import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.cronjob.LeaderElection
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.Sykmeldingstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.logger
import no.nav.syfo.repository.JulesoknadkandidatDAO
import no.nav.syfo.repository.JulesoknadkandidatDAO.Julesoknadkandidat
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.Metrikk
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class JulesoknadService(
    private val metrikk: Metrikk,
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val leaderElection: LeaderElection,
    private val narmesteLederClient: NarmesteLederClient,
    private val aktiverEnkeltSoknadService: AktiverEnkeltSoknadService,
    private val identService: IdentService
) {
    private val log = logger()

    @Transactional
    fun prosseserSoknadsperioder(list: List<SoknadMetadata>): List<SoknadMetadata> {
        if (list.isEmpty()) {
            return list
        }
        if (list.any { p -> p.sykmeldingsperioder.any { it.sykmeldingstype == Sykmeldingstype.REISETILSKUDD } }) {
            return list
        }
        val sortertListe = list.sortedBy { it.fom }
        var forrigeStatus = sortertListe.first().status

        return sortertListe.mapIndexed { index, soknadMetadata ->

            fun erForsteSoknadEllerForrigeErNy(): Boolean {
                if (index == 0) {
                    return true
                }
                return forrigeStatus == Soknadstatus.NY
            }

            if (soknadMetadata.status == Soknadstatus.NY) {
                forrigeStatus = soknadMetadata.status
                return@mapIndexed soknadMetadata
            }
            if (soknadMetadata.harSoknadstypeSomKanGiJulesoknad() &&
                soknadMetadata.erLengreEnn14Dager() &&
                soknadMetadata.fomDatoMellom11novemberOg7desember() &&
                soknadMetadata.tomDatoEtter13Desember()
            ) {
                if (soknadMetadata.arbeidsgiverForskuttererIkke()) {
                    if (erForsteSoknadEllerForrigeErNy()) {
                        metrikk.julesoknadOpprettet()
                        forrigeStatus = Soknadstatus.NY
                        return@mapIndexed soknadMetadata.copy(status = Soknadstatus.NY)
                    } else {
                        log.info("Sykmelding ${soknadMetadata.sykmeldingId} har søknad som kan omfattes av julesøknadregler, men har tidligere søknad som ikke er ny")
                        julesoknadkandidatDAO.lagreJulesoknadkandidat(sykepengesoknadUuid = soknadMetadata.id)
                    }
                } else {
                    log.info("Sykmelding ${soknadMetadata.sykmeldingId} har søknad som kan omfattes av julesøknadregler, men arbeidsgiveren forskutterer")
                    julesoknadkandidatDAO.lagreJulesoknadkandidat(sykepengesoknadUuid = soknadMetadata.id)
                }
            }
            forrigeStatus = soknadMetadata.status
            return@mapIndexed soknadMetadata
        }
    }

    // @Scheduled(cron = "\${julesoknad.cron}")
    fun prosseserJulesoknadKandidater() {
        if (leaderElection.isLeader()) {
            val julesoknadkandidater = julesoknadkandidatDAO.hentJulesoknadkandidater()
            log.info("Prosseserer ${julesoknadkandidater.size} julesoknadkandidater")
            julesoknadkandidater.forEach { julesoknadkandidat ->
                prosseserJulesoknadKandidat(julesoknadkandidat)
            }
        }
    }

    @Transactional
    fun prosseserJulesoknadKandidat(julesoknadkandidat: Julesoknadkandidat) {
        try {
            log.debug("Prosseserer julesoknadkandidat $julesoknadkandidat")

            val soknad = try {
                sykepengesoknadDAO.finnSykepengesoknad(julesoknadkandidat.sykepengesoknadUuid)
            } catch (e: SykepengesoknadDAO.SoknadIkkeFunnetException) {
                log.info("Julesøknadkandidat $julesoknadkandidat sin søknad er ikke lengre i db, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
            if (soknad.status != Soknadstatus.FREMTIDIG) {
                log.info("Julesøknadkandidat $julesoknadkandidat har ikke lengre status fremtidig, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
            val tidligereFremtidigeSoknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                sykmeldingId = soknad.sykmeldingId!!,
            )
                .filter { it.fom!!.isBefore(soknad.fom) }
                .filter { it.status == Soknadstatus.FREMTIDIG }

            if (tidligereFremtidigeSoknader.isNotEmpty()) {
                log.debug("$julesoknadkandidat har tidligere fremtidige søknader på samme sykmelding ")
                return
            }
            if (soknad.arbeidsgiverForskuttererIkke()) {
                log.info("Arbeidsgiver forskutterer ikke julesøknadkandidat $julesoknadkandidat, aktiverer søknad og sletter kandidat")
                aktiverEnkeltSoknadService.aktiverSoknad(soknad.id)
                metrikk.julesoknadAktivertNlEndret()
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
        } catch (e: Exception) {
            log.error("Feil ved prossesering av julesøknadkandidat $julesoknadkandidat", e)
        }
    }

    private fun Sykepengesoknad.arbeidsgiverForskuttererIkke(): Boolean {
        if (this.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
            val orgnummer = this.arbeidsgiverOrgnummer ?: throw RuntimeException("Forventer orgnummer")
            val forskuttering = narmesteLederClient.arbeidsgiverForskutterer(
                sykmeldtFnr = this.fnr,
                orgnummer = orgnummer
            )
            if (forskuttering == Forskuttering.JA) {
                return false
            }
            return true
        }
        return true
    }

    private fun SoknadMetadata.harSoknadstypeSomKanGiJulesoknad(): Boolean =
        !this.sykmeldingsperioder.any { it.sykmeldingstype === Sykmeldingstype.BEHANDLINGSDAGER } &&
            !this.sykmeldingsperioder.any { it.sykmeldingstype === Sykmeldingstype.REISETILSKUDD } &&
            this.soknadstype != Soknadstype.GRADERT_REISETILSKUDD

    private fun SoknadMetadata.arbeidsgiverForskuttererIkke(): Boolean {
        if (this.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
            val orgnummer = this.arbeidsgiverOrgnummer ?: throw RuntimeException("Forventer orgnummer")
            val forskuttering = narmesteLederClient.arbeidsgiverForskutterer(
                sykmeldtFnr = this.fnr,
                orgnummer = orgnummer
            )

            if (forskuttering == Forskuttering.JA) {
                return false
            }
            return true
        }
        return true
    }

    private fun SoknadMetadata.erLengreEnn14Dager(): Boolean = DAYS.between(this.fom, this.tom) >= 14

    private fun SoknadMetadata.fomDatoMellom11novemberOg7desember(): Boolean =
        this.fom.isBeforeOrEqual(LocalDate.of(this.fom.year, 12, 7)) &&
            this.fom.isAfterOrEqual(LocalDate.of(this.fom.year, 11, 15))

    private fun SoknadMetadata.tomDatoEtter13Desember(): Boolean = this.tom.isAfter(LocalDate.of(this.fom.year, 12, 13))
}
