package no.nav.syfo.service

import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.logger
import no.nav.syfo.repository.SporsmalDAO
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.util.DatoUtil.datoErInnenforMinMax
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
@Transactional
class EndreGrenseverdiService(
    private val sporsmalDAO: SporsmalDAO,
    private val hentSoknadService: HentSoknadService
) {
    private val log = logger()

    fun oppdaterGrenseverdi(soknader: List<Sykepengesoknad>, identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        val soknaderMedFeilGrense = soknader.filter {
            harSporsmalMedFeilGrenseverdi(it)
        }
        if (soknaderMedFeilGrense.isEmpty()) return soknader

        soknaderMedFeilGrense.forEach {
            val sporsmal = it.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR)
            if (sporsmal?.id == null) {
                log.warn("Søknad med id ${it.id} inneholder ikke spørsmål om PERMITTERT_NAA_NAR")
            } else {
                sporsmalDAO.oppdaterSporsmalGrense(sporsmal.id, LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE))
                log.info("Fullførte oppdatering av grenseverdi for søknad ${it.id}")
            }
        }

        return hentSoknadService.hentSoknader(identer)
    }

    private fun harSporsmalMedFeilGrenseverdi(soknad: Sykepengesoknad): Boolean {
        val sporsmal = soknad.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR)
        if (sporsmal == null) return false

        val harFeilGrense = !datoErInnenforMinMax(LocalDate.of(2020, 2, 1), sporsmal.min, sporsmal.max)
        if (harFeilGrense) {
            log.info("Søknad ${soknad.id} har min grenseverdi ${sporsmal.min}")
        }
        return harFeilGrense
    }
}
