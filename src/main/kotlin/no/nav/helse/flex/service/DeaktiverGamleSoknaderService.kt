package no.nav.helse.flex.service

import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.repository.SporsmalDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class DeaktiverGamleSoknaderService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sporsmalDao: SporsmalDAO,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    private val log = logger()

    fun deaktiverSoknader(): Int {
        val soknaderSomSkalDeaktiveres = sykepengesoknadDAO.soknaderSomSkalDeaktiveres()
        log.info("Skal deaktiverer ${soknaderSomSkalDeaktiveres.size} søknader.")

        sporsmalDao.slettSporsmal(soknaderSomSkalDeaktiveres.map { it.sykepengesoknadId })

        soknaderSomSkalDeaktiveres.forEach { soknadSomSkalDeaktiveres ->
            medlemskapVurderingRepository.deleteBySykepengesoknadId(soknadSomSkalDeaktiveres.sykepengesoknadUuid)
        }

        val antall = sykepengesoknadDAO.deaktiverSoknader()
        log.info("Deaktiverte $antall søknader.")
        return antall
    }
}
