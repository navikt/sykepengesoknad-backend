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

fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean? {
    return if (!harSvartJaPaPermisjonUtland(sykepengesoknad) || !harSvartJaPaUtland(sykepengesoknad)) {
        null
    } else {
        sykepengesoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
            .map { it.forsteSvar }
            .map { "JA" == it }
            .orElse(null)
    }
}

private fun harSvartJaPaPermisjonUtland(sykepengesoknad: Sykepengesoknad): Boolean {
    val feriePermisjonUtland = sykepengesoknad.getOptionalSporsmalMedTag(FERIE_PERMISJON_UTLAND)
    return feriePermisjonUtland.isPresent && "JA" == feriePermisjonUtland.get().forsteSvar
}

private fun harSvartJaPaUtland(sykepengesoknad: Sykepengesoknad): Boolean {
    val utland = sykepengesoknad.getOptionalSporsmalMedTag(UTLAND)
    val utlandV2 = sykepengesoknad.getOptionalSporsmalMedTag(UTLAND_V2)
    return utland.isPresent && "CHECKED" == utland.get().forsteSvar || utlandV2.isPresent && "CHECKED" == utlandV2.get().forsteSvar
}

internal fun getFaktiskGrad(
    faktiskTimer: Double?,
    avtaltTimer: Double?,
    periode: Soknadsperiode,
    ferieOgPermisjonPerioder: List<FravarDTO>,
    arbeidgjenopptattDato: LocalDate?
): Int? {
    val antallVirkedagerPerUke = 5

    val virkedager = antallVirkedagerIPeriode(periode, arbeidgjenopptattDato) - antallVirkedagerIPerioder(ferieOgPermisjonPerioder, periode)

    return if (faktiskTimer == null || avtaltTimer == null || virkedager == 0) {
        null
    } else {
        Math.toIntExact(Math.round(faktiskTimer / (avtaltTimer / antallVirkedagerPerUke * virkedager) * 100))
    }
}

private fun antallVirkedagerIPeriode(periode: Soknadsperiode, arbeidgjenopptattDato: LocalDate?): Int {
    var virkedager = 0

    val slutt = if (arbeidgjenopptattDato == null) {
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

private fun erIkkeHelgedag(dag: LocalDate): Boolean {
    return dag.dayOfWeek != DayOfWeek.SATURDAY && dag.dayOfWeek != DayOfWeek.SUNDAY
}

private fun antallVirkedagerIPerioder(ferieOgPermisjonPerioder: List<FravarDTO>, soknadsperiode: Soknadsperiode): Int {
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

fun samleFravaerListe(soknad: Sykepengesoknad): List<FravarDTO> {
    return hentFeriePermUtlandListe(soknad)
}

fun hentFeriePermUtlandListe(sykepengesoknad: Sykepengesoknad): List<FravarDTO> {
    val fravarliste = ArrayList<FravarDTO>()

    if (sykepengesoknad.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND)?.forsteSvar == "JA") {
        // TODO: Sjekk hvorfor denne skal returnerer tom liste
        return fravarliste
    }

    sykepengesoknad.getSporsmalMedTagOrNull(FERIE)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(FERIE_NAR), FravarstypeDTO.FERIE))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(PERMISJON)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR), FravarstypeDTO.PERMISJON))
    }

    sykepengesoknad.getSporsmalMedTagOrNull(UTLAND)?.takeIf { it.forsteSvar == "CHECKED" }?.let {
        sykepengesoknad.getSporsmalMedTagOrNull(UTLAND_NAR)?.let { undersporsmal ->
            fravarliste.addAll(hentFravar(undersporsmal, FravarstypeDTO.UTLANDSOPPHOLD))
        }
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

    return fravarliste
}

private fun hentFravar(sporsmal: Sporsmal, fravartype: FravarstypeDTO): List<FravarDTO> {
    val fravarliste = ArrayList<FravarDTO>()
    val svarliste = sporsmal.svar

    for (svar in svarliste) {
        val (fom, tom) = svar.verdi.getJsonPeriode()
        fravarliste.add(
            FravarDTO(
                fom,
                tom,
                fravartype
            )
        )
    }
    return fravarliste
}
