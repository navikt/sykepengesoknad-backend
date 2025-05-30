package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.getJsonPeriode
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.ChronoUnit
import java.util.*

internal fun getFaktiskGrad(
    faktiskTimer: Double?,
    avtaltTimer: Double?,
    periode: Soknadsperiode,
    ferieOgPermisjonPerioder: List<FravarDTO>,
    arbeidgjenopptattDato: LocalDate?,
): Int? {
    val antallVirkedagerPerUke = 5

    val virkedager = antallVirkedagerIPeriode(periode, arbeidgjenopptattDato) - antallVirkedagerIPerioder(ferieOgPermisjonPerioder, periode)

    return if (faktiskTimer == null || avtaltTimer == null || virkedager == 0) {
        null
    } else {
        Math.toIntExact(Math.round(faktiskTimer / (avtaltTimer / antallVirkedagerPerUke * virkedager) * 100))
    }
}

private fun antallVirkedagerIPeriode(
    periode: Soknadsperiode,
    arbeidgjenopptattDato: LocalDate?,
): Int {
    var virkedager = 0

    val slutt =
        if (arbeidgjenopptattDato == null) {
            Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1)
        } else {
            Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, arbeidgjenopptattDato))
        }

    for (i in 0 until slutt) {
        if (erIkkeHelgedag(periode.fom.plusDays(i.toLong()))) {
            virkedager++
        }
    }

    return virkedager
}

private fun erIkkeHelgedag(dag: LocalDate): Boolean = dag.dayOfWeek != DayOfWeek.SATURDAY && dag.dayOfWeek != DayOfWeek.SUNDAY

private fun antallVirkedagerIPerioder(
    ferieOgPermisjonPerioder: List<FravarDTO>,
    soknadsperiode: Soknadsperiode,
): Int {
    val virkedager = HashSet<LocalDate>()

    ferieOgPermisjonPerioder.forEach { (fom, tom) ->
        val slutt = Math.toIntExact(ChronoUnit.DAYS.between(fom!!, tom) + 1)

        for (i in 0 until slutt) {
            val current = fom.plusDays(i.toLong())
            if (erIkkeHelgedag(current) &&
                !current.isBefore(soknadsperiode.fom) &&
                !current.isAfter(soknadsperiode.tom)
            ) {
                virkedager.add(current)
            }
        }
    }

    return virkedager.size
}

internal fun arbeidGjenopptattDato(sykepengesoknad: Sykepengesoknad): LocalDate? {
    sykepengesoknad.getSporsmalMedTagOrNull(TILBAKE_NAR)?.forsteSvar?.let {
        if ("JA" == sykepengesoknad.getSporsmalMedTag(TILBAKE_I_ARBEID).forsteSvar) {
            return LocalDate.parse(it, ISO_LOCAL_DATE)
        }
    }
    return null
}

fun samleFravaerListe(soknad: Sykepengesoknad): List<FravarDTO> = hentFeriePermUtlandListe(soknad)

fun hentFeriePermUtlandListe(sykepengesoknad: Sykepengesoknad): List<FravarDTO> {
    val fravarliste = ArrayList<FravarDTO>()

    sykepengesoknad.getSporsmalMedTagOrNull(FERIE)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(FERIE_NAR), FravarstypeDTO.FERIE))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(PERMISJON)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR), FravarstypeDTO.PERMISJON))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(UTLAND)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(UTLAND_NAR), FravarstypeDTO.UTLANDSOPPHOLD))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(ARBEIDSLEDIG_UTLAND)?.takeIf { it.forsteSvar == "JA" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(UTLAND_NAR), FravarstypeDTO.UTLANDSOPPHOLD))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(FERIE_V2)?.takeIf { it.forsteSvar == "JA" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2), FravarstypeDTO.FERIE))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(PERMISJON_V2)?.takeIf { it.forsteSvar == "JA" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR_V2), FravarstypeDTO.PERMISJON))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(UTLAND_V2)?.takeIf { it.forsteSvar == "JA" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2), FravarstypeDTO.UTLANDSOPPHOLD))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(OPPHOLD_UTENFOR_EOS)?.takeIf { it.forsteSvar == "JA" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS_NAR), FravarstypeDTO.UTLANDSOPPHOLD))
    }

    return fravarliste
}

private fun hentFravar(
    sporsmal: Sporsmal,
    fravartype: FravarstypeDTO,
): List<FravarDTO> {
    val fravarliste = ArrayList<FravarDTO>()
    val svarliste = sporsmal.svar

    for (svar in svarliste) {
        val (fom, tom) = svar.verdi.getJsonPeriode()
        fravarliste.add(
            FravarDTO(
                fom,
                tom,
                fravartype,
            ),
        )
    }
    return fravarliste
}
