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
import kotlin.math.roundToInt

internal fun beregnFaktiskGrad(
    timerJobbetIPerioden: Double?,
    avtaltTimerPerUke: Double?,
    periode: Soknadsperiode,
    ferieOgPermisjonPerioder: List<FravarDTO>,
    arbeidgjenopptattDato: LocalDate?,
): Int? {
    val virkedagerPerUke = 5

    // Finn antall virkedager i perioden (mandag–fredag), justert for eventuell dato der arbeid ble gjenopptatt
    // og trekk fra virkedager som faller innenfor ferie-/permisjonsperioder.
    val faktiskeVirkedager =
        finnVirkedagerIPerioden(periode, arbeidgjenopptattDato) -
            antallVirkedagerIPerioden(
                ferieOgPermisjonPerioder,
                periode,
            )

    return if (timerJobbetIPerioden == null || avtaltTimerPerUke == null || faktiskeVirkedager == 0) {
        null
    } else {
        ((timerJobbetIPerioden / (avtaltTimerPerUke / virkedagerPerUke * faktiskeVirkedager) * 100)).roundToInt()
    }
}

private fun finnVirkedagerIPerioden(
    periode: Soknadsperiode,
    arbeidGjenopptattDato: LocalDate?,
): Int {
    var virkedagerIPerioden = 0

    val antallDagerIPerioden =
        if (arbeidGjenopptattDato == null) {
            Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1)
        } else {
            Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, arbeidGjenopptattDato))
        }

    for (i in 0 until antallDagerIPerioden) {
        if (erIkkeHelg(periode.fom.plusDays(i.toLong()))) {
            virkedagerIPerioden++
        }
    }

    return virkedagerIPerioden
}

private fun antallVirkedagerIPerioden(
    ferieOgPermisjonPerioder: List<FravarDTO>,
    soknadsperiode: Soknadsperiode,
): Int {
    val virkedager = HashSet<LocalDate>()

    ferieOgPermisjonPerioder.forEach { (fom, tom) ->
        val slutt = Math.toIntExact(ChronoUnit.DAYS.between(fom!!, tom) + 1)

        for (i in 0 until slutt) {
            val current = fom.plusDays(i.toLong())
            if (erIkkeHelg(current) &&
                !current.isBefore(soknadsperiode.fom) &&
                !current.isAfter(soknadsperiode.tom)
            ) {
                virkedager.add(current)
            }
        }
    }

    return virkedager.size
}

private fun erIkkeHelg(dag: LocalDate): Boolean = dag.dayOfWeek != DayOfWeek.SATURDAY && dag.dayOfWeek != DayOfWeek.SUNDAY

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
        fravarliste.addAll(
            hentFravar(
                sykepengesoknad.getSporsmalMedTag(OPPHOLD_UTENFOR_EOS_NAR),
                FravarstypeDTO.UTLANDSOPPHOLD,
            ),
        )
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
