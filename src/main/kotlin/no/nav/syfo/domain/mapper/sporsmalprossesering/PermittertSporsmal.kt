package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.getJsonPeriode
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE_NAR
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Sykepengesoknad.hentPermitteringer(): List<PeriodeDTO> {

    val periodeListe = mutableListOf<PeriodeDTO>()

    this.getSporsmalMedTagOrNull(PERMITTERT_NAA)?.let { erDuPermittertNaa ->
        if (erDuPermittertNaa.forsteSvar == "JA") {
            this.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR)?.let { spm ->
                spm.forsteSvar?.let { svar ->
                    val fom = LocalDate.parse(svar, DateTimeFormatter.ISO_LOCAL_DATE)
                    periodeListe.add(PeriodeDTO(fom = fom))
                }
            }
        }
    }

    this.getSporsmalMedTagOrNull(PERMITTERT_PERIODE)?.let { erDuPermittertIPerioden ->
        if (erDuPermittertIPerioden.forsteSvar == "JA") {
            this.getSporsmalMedTagOrNull(PERMITTERT_PERIODE_NAR)?.let { periodesporsmal ->
                periodesporsmal.svar.map { svar -> svar.verdi.getJsonPeriode() }.forEach { periodeListe.add(it) }
            }
        }
    }

    return periodeListe
}
