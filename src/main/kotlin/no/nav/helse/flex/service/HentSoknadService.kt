package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.tilSlutt
import org.slf4j.Logger
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

    fun hentSoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> = sykepengesoknadDAO.finnSykepengesoknader(identer)

    fun hentSoknaderUtenSporsmal(identer: FolkeregisterIdenter): List<Sykepengesoknad> =
        sykepengesoknadDAO.finnSykepengesoknaderUtenSporsmal(identer.alle())

    fun finnSykepengesoknad(uuid: String): Sykepengesoknad {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(uuid)
        if (patchSoknad(soknad)) {
            return sykepengesoknadDAO.finnSykepengesoknad(uuid)
        }
        return soknad
    }

    private fun patchSoknad(soknad: Sykepengesoknad): Boolean {
        val patchetSoknad =
            soknad
                .handterUtland(log)
                .patchSvartypeOppsummering(log)
                .fjernBekreftOpplysninger(log)
        if (soknad != patchetSoknad) {
            sykepengesoknadDAO.byttUtSporsmal(patchetSoknad)
            return true
        }
        return false
    }

    fun hentEldsteSoknaden(
        identer: FolkeregisterIdenter,
        fom: LocalDate?,
    ): String? = sykepengesoknadRepository.findEldsteSoknaden(identer.alle(), fom)
}

private fun Sykepengesoknad.handterUtland(log: Logger): Sykepengesoknad {
    if (this.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS) != null &&
        (
            this.getSporsmalMedTagOrNull(UTLAND_V2) != null ||
                this.getSporsmalMedTagOrNull(UTLAND) != null ||
                this.getSporsmalMedTagOrNull(ARBEIDSLEDIG_UTLAND) != null
        )
    ) {
        log.info("Fjernet nytt spørsmål med tag OPPHOLD_UTENFOR_EOS fra søknad med id ${this.id}")
        return this.fjernSporsmal(OPPHOLD_UTENFOR_EOS)
    }
    return this
}

private fun Sykepengesoknad.fjernBekreftOpplysninger(log: Logger): Sykepengesoknad {
    if (this.getSporsmalMedTagOrNull(BEKREFT_OPPLYSNINGER) != null) {
        log.info("Fjernet spørsmål med tag $BEKREFT_OPPLYSNINGER fra søknad med id ${this.id}")
        return this.fjernSporsmal(BEKREFT_OPPLYSNINGER)
    }
    return this
}

private fun Sykepengesoknad.patchSvartypeOppsummering(log: Logger): Sykepengesoknad {
    if (this.alleSporsmalOgUndersporsmal().any { it.svartype == Svartype.BEKREFTELSESPUNKTER }) {
        log.info("Fjernet spørsmål med svartype $BEKREFTELSESPUNKTER fra søknad med id ${this.id}")
        return this.leggTilSporsmaal(tilSlutt())
    }
    return this
}
