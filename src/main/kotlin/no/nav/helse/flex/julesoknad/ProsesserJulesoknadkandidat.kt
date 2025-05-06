package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringProducer
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.repository.JulesoknadkandidatDAO.Julesoknadkandidat
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.service.IdentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProsesserJulesoknadkandidat(
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val forskutteringRepository: ForskutteringRepository,
    private val aktiveringProducer: AktiveringProducer,
    private val identService: IdentService,
) {
    private val log = logger()

    @Transactional
    fun prosseserJulesoknadKandidat(julesoknadkandidat: Julesoknadkandidat) {
        try {
            log.debug("Prosseserer julesoknadkandidat {}", julesoknadkandidat)

            val soknad = sykepengesoknadRepository.findBySykepengesoknadUuid(julesoknadkandidat.sykepengesoknadUuid)
            if (soknad == null) {
                log.info("Julesøknadkandidat $julesoknadkandidat sin søknad er ikke lengre i db, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }

            if (soknad.status != Soknadstatus.FREMTIDIG) {
                log.info("Julesøknadkandidat $julesoknadkandidat har ikke lengre status fremtidig, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }

            if (soknad.arbeidsgiverForskuttererIkke()) {
                val folkeregisterIdenter = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
                val tidligereFremtidigeSoknader =
                    sykepengesoknadDAO
                        .finnSykepengesoknader(
                            folkeregisterIdenter,
                        ).filter { it.soknadstype != Soknadstype.OPPHOLD_UTLAND }
                        .filter { it.fom != null }
                        .filter { it.fom!!.isBefore(soknad.fom) }
                        .filter { it.status == Soknadstatus.FREMTIDIG }

                if (tidligereFremtidigeSoknader.isNotEmpty()) {
                    log.info("$julesoknadkandidat har tidligere fremtidige søknader, kan derfor ikke aktivere julesøknad")
                    return
                }
                log.info("Arbeidsgiver forskutterer ikke julesøknadkandidat $julesoknadkandidat, aktiverer søknad og sletter kandidat")

                // Gjør at vi kan identifisere søknaden som en julesøknad i sykepengesoknad-frontend.
                sykepengesoknadRepository.settErAktivertJulesoknadKandidat(soknad.id!!)

                aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(soknad.fnr, soknad.sykepengesoknadUuid))
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
        } catch (e: Exception) {
            log.error("Feil ved prossesering av julesøknadkandidat $julesoknadkandidat", e)
        }
    }

    private fun SykepengesoknadDbRecord.arbeidsgiverForskuttererIkke(): Boolean {
        if (this.arbeidssituasjon == no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER) {
            val orgnummer = this.arbeidsgiverOrgnummer ?: throw RuntimeException("Forventer orgnummer")

            val forskuttering =
                forskutteringRepository
                    .finnForskuttering(
                        brukerFnr = this.fnr,
                        orgnummer = orgnummer,
                    )?.arbeidsgiverForskutterer
            if (forskuttering == true) {
                return false
            }
            return true
        }
        return true
    }
}
