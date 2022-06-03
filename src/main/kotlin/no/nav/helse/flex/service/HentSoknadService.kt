package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class HentSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository
) {
    val log = logger()

    fun hentSoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadDAO.finnSykepengesoknader(identer)
    }

    fun finnSykepengesoknad(uuid: String): Sykepengesoknad {
        return sykepengesoknadDAO.finnSykepengesoknad(uuid)
    }

    fun hentEldsteSoknaden(identer: FolkeregisterIdenter): String {
        return sykepengesoknadRepository.findEldsteSoknaden(identer.alle())
    }
}
