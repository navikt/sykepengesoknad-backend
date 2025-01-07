package no.nav.helse.flex.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.repository.SporsmalDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeaktiverGamleSoknaderService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sporsmalDao: SporsmalDAO,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    private val log = logger()

    @Transactional
    fun deaktiverSoknader(): Int {
        val deaktiverteSoknader = sykepengesoknadDAO.deaktiverSoknader()

        sporsmalDao.slettSporsmalOgSvar(deaktiverteSoknader.map { it.sykepengesoknadId })

        deaktiverteSoknader.forEach { soknadSomSkalDeaktiveres ->
            medlemskapVurderingRepository.deleteBySykepengesoknadId(soknadSomSkalDeaktiveres.sykepengesoknadUuid)
        }

        log.info("Deaktiverte ${deaktiverteSoknader.size} søknader.")
        return deaktiverteSoknader.size
    }
}
