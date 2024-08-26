package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class HentSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    val log = logger()

    fun hentSoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadDAO.finnSykepengesoknader(identer)
    }

    fun hentSoknaderUtenSporsmal(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadDAO.finnSykepengesoknaderUtenSporsmal(identer.alle())
    }

    fun finnSykepengesoknad(uuid: String): Sykepengesoknad {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        handterUtland(soknad)
        fjernBekreftOpplysninger(soknad)
        return sykepengesoknadDAO.finnSykepengesoknad(uuid)
    }

    private fun fjernBekreftOpplysninger(soknad: Sykepengesoknad) {
        if (soknad.getSporsmalMedTagOrNull(BEKREFT_OPPLYSNINGER) != null) {
            val soknadUtenBekreft = soknad.fjernSporsmal(BEKREFT_OPPLYSNINGER)
            sykepengesoknadDAO.byttUtSporsmal(soknadUtenBekreft)
            log.info("Fjernet nytt spørsmål med tag $BEKREFT_OPPLYSNINGER fra søknad med id ${soknadUtenBekreft.id}")
        }
    }

    private fun handterUtland(soknad: Sykepengesoknad) {
        if (soknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS) != null &&
            (
                soknad.getSporsmalMedTagOrNull(UTLAND_V2) != null ||
                    soknad.getSporsmalMedTagOrNull(UTLAND) != null ||
                    soknad.getSporsmalMedTagOrNull(ARBEIDSLEDIG_UTLAND) != null
            )
        ) {
            val soknadUtenNyttSporsmal = soknad.fjernSporsmal(OPPHOLD_UTENFOR_EOS)
            sykepengesoknadDAO.byttUtSporsmal(soknadUtenNyttSporsmal)
            log.info("Fjernet nytt spørsmål med tag OPPHOLD_UTENFOR_EOS fra søknad med id ${soknadUtenNyttSporsmal.id}")
        }
    }

    fun hentEldsteSoknaden(
        identer: FolkeregisterIdenter,
        fom: LocalDate?,
    ): String? {
        return sykepengesoknadRepository.findEldsteSoknaden(identer.alle(), fom)
    }
}
