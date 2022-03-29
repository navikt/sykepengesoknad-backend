package no.nav.syfo.service

import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class HentSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO
) {
    val log = logger()

    fun hentSoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadDAO.finnSykepengesoknader(identer)
    }

    fun finnSykepengesoknad(uuid: String): Sykepengesoknad {
        return sykepengesoknadDAO.finnSykepengesoknad(uuid)
    }
}
