package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Sykmeldingstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.ENKELTSTAENDE_BEHANDLINGSDAGER
import no.nav.syfo.soknadsopprettelse.ENKELTSTAENDE_BEHANDLINGSDAGER_UKE
import no.nav.syfo.util.*
import no.nav.syfo.util.DatoUtil.formatterPeriode
import java.time.LocalDate

fun behandlingsdagerSporsmal(soknadMetadata: SoknadMetadata): List<Sporsmal> {
    return soknadMetadata.sykmeldingsperioder
        .filter { it.sykmeldingstype == Sykmeldingstype.BEHANDLINGSDAGER }
        .sortedBy { it.fom }
        .lastIndex.downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadMetadata.sykmeldingsperioder[index]
            val uker = splittPeriodeIUker(periode)

            val sporsmalstekst = if (soknadMetadata.arbeidssituasjon == Arbeidssituasjon.ARBEIDSLEDIG) {
                "Hvilke dager kunne du ikke være arbeidssøker på grunn av behandling mellom ${formatterPeriode(periode.fom, periode.tom)}?"
            } else {
                "Hvilke dager måtte du være helt borte fra jobben på grunn av behandling mellom ${formatterPeriode(periode.fom, periode.tom)}?"
            }

            Sporsmal(
                tag = ENKELTSTAENDE_BEHANDLINGSDAGER + index,
                sporsmalstekst = sporsmalstekst,
                svartype = Svartype.INFO_BEHANDLINGSDAGER,
                undersporsmal = skapUndersporsmalUke(uker)
            )
        }
}

private fun skapUndersporsmalUke(uker: List<Uke>): List<Sporsmal> {
    return uker
        .sortedBy { it.ukestart }
        .lastIndex.downTo(0)
        .reversed()
        .map { ukeIndex ->
            val uke = uker[ukeIndex]
            val sporsmalstekst =
                if (uke.ukestart.isBefore(uke.ukeslutt)) {
                    "${uke.ukestart.datoMånedÅrFormat()} - ${uke.ukeslutt.datoMånedÅrFormat()}"
                } else {
                    "${uke.ukestart.datoMånedÅrFormat()}"
                }
            Sporsmal(
                sporsmalstekst = sporsmalstekst,
                tag = ENKELTSTAENDE_BEHANDLINGSDAGER_UKE + ukeIndex,
                svartype = Svartype.RADIO_GRUPPE_UKEKALENDER,
                min = "${uke.ukestart}",
                max = "${uke.ukeslutt}"
            )
        }
}

internal fun splittPeriodeIUker(periode: Soknadsperiode): List<Uke> {

    if (periode.fom.isAfter(periode.tom)) {
        throw IllegalArgumentException("Fom kan ikke være etter tom i periode $periode")
    }

    val uker = ArrayList<Uke>()
    var ukestart = periode.fom.forsteHverdag()
    do {
        val uke = Uke(ukestart, min(ukestart.fredagISammeUke(), periode.tom))
        uker.add(uke)
        ukestart = uke.ukeslutt.plusDays(1).forsteHverdag()
    } while (periode.tom.isAfterOrEqual(ukestart))
    return uker
}

data class Uke(val ukestart: LocalDate, val ukeslutt: LocalDate)
