package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.getJsonPeriode
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.Collections.emptyList

fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean? {
    return if (!harSvartJaPaPermisjonUtland(sykepengesoknad) || !harSvartJaPaUtland(sykepengesoknad)) {
        null
    } else sykepengesoknad.getOptionalSporsmalMedTag(UTLANDSOPPHOLD_SOKT_SYKEPENGER)
        .map { it.forsteSvar }
        .map { "JA" == it }
        .orElse(null)
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

internal fun getStillingsprosent(
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
    } else Math.toIntExact(Math.round(faktiskTimer / (avtaltTimer / antallVirkedagerPerUke * virkedager) * 100))
}

private fun antallVirkedagerIPeriode(periode: Soknadsperiode, arbeidgjenopptattDato: LocalDate?): Int {
    var virkedager = 0

    val slutt = if (arbeidgjenopptattDato == null)
        Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1)
    else
        Math.toIntExact(ChronoUnit.DAYS.between(periode.fom, arbeidgjenopptattDato))

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
        if ("JA" == sykepengesoknad.getSporsmalMedTag(TILBAKE_I_ARBEID).forsteSvar)
            return LocalDate.parse(it, ISO_LOCAL_DATE)
    }
    return null
}

fun samleFravaerListe(soknad: Sykepengesoknad): List<FravarDTO> {
    val fravaerliste = hentFeriePermUtlandListe(soknad).toMutableList()
    finnUtdanning(soknad).ifPresent { fravaerliste.add(it) }
    return fravaerliste
}

internal fun finnUtdanning(soknad: Sykepengesoknad): Optional<FravarDTO> {
    if (!soknad.getOptionalSporsmalMedTag(UTDANNING).isPresent) {
        return Optional.empty()
    }

    val startsvar = soknad.getSporsmalMedTag(UTDANNING_START).forsteSvar
    if ("JA" != soknad.getSporsmalMedTag(UTDANNING).forsteSvar || startsvar == null) {
        return Optional.empty()
    }
    val fravar = if ("JA" == soknad.getSporsmalMedTag(FULLTIDSSTUDIUM).forsteSvar) FravarstypeDTO.UTDANNING_FULLTID else FravarstypeDTO.UTDANNING_DELTID
    return Optional.of(
        FravarDTO(
            LocalDate.parse(startsvar, ISO_LOCAL_DATE), null,
            fravar
        )
    )
}

fun hentFeriePermUtlandListe(sykepengesoknad: Sykepengesoknad): List<FravarDTO> {
    val fravarliste = ArrayList<FravarDTO>()

    val feriePerimisjonUtland = sykepengesoknad.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND)

    if (feriePerimisjonUtland != null) {
        if ("JA" != feriePerimisjonUtland.forsteSvar) {
            return fravarliste
        }
        sykepengesoknad.getOptionalSporsmalMedTag(FERIE).ifPresent { sporsmal ->
            if ("CHECKED" == sporsmal.forsteSvar) {
                fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(FERIE_NAR), FravarstypeDTO.FERIE))
            }
        }
        sykepengesoknad.getOptionalSporsmalMedTag(PERMISJON).ifPresent { sporsmal ->
            if ("CHECKED" == sporsmal.forsteSvar) {
                fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR), FravarstypeDTO.PERMISJON))
            }
        }
        sykepengesoknad.getOptionalSporsmalMedTag(UTLAND).ifPresent { sporsmal ->
            if ("CHECKED" == sporsmal.forsteSvar) {
                sykepengesoknad.getOptionalSporsmalMedTag(UTLAND_NAR).ifPresent { undersporsmal ->
                    fravarliste.addAll(
                        hentFravar(undersporsmal, FravarstypeDTO.UTLANDSOPPHOLD)
                    )
                }
            }
        }
    } else {
        if (harFeriesporsmal(sykepengesoknad) && "JA" == sykepengesoknad.getSporsmalMedTag(FERIE_V2).forsteSvar) {
            fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(FERIE_NAR_V2), FravarstypeDTO.FERIE))
        }
        if (harPermisjonsporsmal(sykepengesoknad) && "JA" == sykepengesoknad.getSporsmalMedTag(PERMISJON_V2).forsteSvar) {
            fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(PERMISJON_NAR_V2), FravarstypeDTO.PERMISJON))
        }
        if (harUtlandsporsmal(sykepengesoknad) && "JA" == sykepengesoknad.getSporsmalMedTag(UTLAND_V2).forsteSvar) {
            fravarliste.addAll(hentFravar(sykepengesoknad.getSporsmalMedTag(UTLAND_NAR_V2), FravarstypeDTO.UTLANDSOPPHOLD))
        }
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

fun hentInntektListeArbeidstaker(soknad: Sykepengesoknad): List<InntektskildeDTO> {
    val andreinntektsporsmal = soknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER)
    return if ("JA" == andreinntektsporsmal?.forsteSvar)
        andreinntektsporsmal.undersporsmal[0].undersporsmal
            .filter { (_, _, _, _, _, _, _, _, _, svar) -> !svar.isEmpty() }
            .map { sporsmal ->
                InntektskildeDTO(
                    mapSporsmalTilInntektskildetype(sporsmal),
                    if (sporsmal.undersporsmal.isEmpty())
                        null
                    else
                        "JA" == sporsmal.undersporsmal[0].forsteSvar
                )
            }
    else
        emptyList()
}

private fun mapSporsmalTilInntektskildetype(sporsmal: Sporsmal): InntektskildetypeDTO {
    when (sporsmal.tag) {
        INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD -> return InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD
        INNTEKTSKILDE_SELVSTENDIG -> return InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE
        INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA -> return InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA
        INNTEKTSKILDE_JORDBRUKER -> return InntektskildetypeDTO.JORDBRUKER_FISKER_REINDRIFTSUTOVER
        INNTEKTSKILDE_FRILANSER -> return InntektskildetypeDTO.FRILANSER
        INNTEKTSKILDE_ANNET -> return InntektskildetypeDTO.ANNET
        INNTEKTSKILDE_FOSTERHJEM -> return InntektskildetypeDTO.FOSTERHJEMGODTGJORELSE
        INNTEKTSKILDE_OMSORGSLONN -> return InntektskildetypeDTO.OMSORGSLONN
        INNTEKTSKILDE_ARBEIDSFORHOLD -> return InntektskildetypeDTO.ARBEIDSFORHOLD
        INNTEKTSKILDE_FRILANSER_SELVSTENDIG -> return InntektskildetypeDTO.FRILANSER_SELVSTENDIG
        else -> throw RuntimeException("Inntektskildetype " + sporsmal.tag + " finnes ikke i DTO")
    }
}
