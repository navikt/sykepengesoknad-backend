package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.repository.JulesoknadkandidatDAO.Julesoknadkandidat
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProsesserJulesoknadkandidat(
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
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
        } catch (e: Exception) {
            log.error("Feil ved prossesering av julesøknadkandidat $julesoknadkandidat", e)
        }
    }
}
