package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.finnSoknadsType
import no.nav.helse.flex.repository.KlippVariant
import no.nav.helse.flex.soknadsopprettelse.antallDager
import no.nav.helse.flex.soknadsopprettelse.eldstePeriodeFOM
import no.nav.helse.flex.soknadsopprettelse.hentSenesteTOMFraPerioder
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.EndringIUforegrad
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.KlippMetrikk
import no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.overlap
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.helse.flex.util.min
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.floor

fun ArbeidsgiverSykmelding.splittSykmeldingiSoknadsPerioder(
    arbeidssituasjon: Arbeidssituasjon,
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykmeldingId: String,
    behandletTidspunkt: Instant,
    orgnummer: String?,
    klippMetrikk: KlippMetrikk
): List<Tidsenhet> {
    val sykmeldingTidsenheter = SykmeldingTidsenheter(
        mutableListOf(),
        mutableListOf(
            Tidsenhet(
                fom = eldstePeriodeFOM(perioder = sykmeldingsperioder),
                tom = hentSenesteTOMFraPerioder(perioder = sykmeldingsperioder)
            )
        )
    )

    if (harBehandlingsdager(arbeidssituasjon)) {
        sykmeldingTidsenheter.splittLangeSykmeldingperioderMedBehandlingsdager()
    }

    if (erArbeidstakerSoknad(arbeidssituasjon)) {
        sykmeldingTidsenheter.splittPeriodenSomOverlapperSendtSoknad(
            eksisterendeSoknader,
            sykmeldingId,
            behandletTidspunkt,
            orgnummer,
            klippMetrikk
        )
    }

    sykmeldingTidsenheter.splittLangeSykmeldingperioder()

    if (sykmeldingTidsenheter.splittbar.isNotEmpty()) {
        throw RuntimeException("Kan ikke opprette søknader for sykmelding $sykmeldingId da den fremdeles har splittbare perioder")
    }

    return sykmeldingTidsenheter.ferdigsplittet.sortedBy { it.fom }
}

private fun ArbeidsgiverSykmelding.harBehandlingsdager(arbeidssituasjon: Arbeidssituasjon): Boolean {
    return finnSoknadsType(arbeidssituasjon, sykmeldingsperioder) == Soknadstype.BEHANDLINGSDAGER
}

private fun ArbeidsgiverSykmelding.erArbeidstakerSoknad(arbeidssituasjon: Arbeidssituasjon): Boolean {
    return finnSoknadsType(arbeidssituasjon, sykmeldingsperioder) == Soknadstype.ARBEIDSTAKERE
}

private fun SykmeldingTidsenheter.splittLangeSykmeldingperioderMedBehandlingsdager(): SykmeldingTidsenheter {
    while (splittbar.isNotEmpty()) {
        val tidsenhet = splittbar.removeFirst()
        ferdigsplittet.addAll(splittPeriodeBasertPaaUke(tidsenhet))
    }
    return this
}

private fun SykmeldingTidsenheter.splittPeriodenSomOverlapperSendtSoknad(
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykmeldingId: String,
    behandletTidspunkt: Instant,
    orgnummer: String?,
    klippMetrikk: KlippMetrikk
): SykmeldingTidsenheter {
    val splittbareTidsenheter = splittbar.toMutableList()
    splittbar.clear()

    val soknadskandidater = eksisterendeSoknader.asSequence()
        .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
        .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
        .filter { it.status == Soknadstatus.SENDT }
        .filter { it.sykmeldingSkrevet!!.isBefore(behandletTidspunkt) }
        .filter { it.arbeidsgiverOrgnummer == orgnummer }
        .filter { sok ->
            splittbareTidsenheter.forEach {
                val soknadPeriode = sok.fom!!..sok.tom!!
                val sykmeldingPeriode = it.fom..it.tom
                if (sykmeldingPeriode.overlap(soknadPeriode)) {
                    return@filter true
                }
            }
            return@filter false
        }
        .toList()

    while (splittbareTidsenheter.isNotEmpty()) {
        var tidsenhet = splittbareTidsenheter.removeFirst()

        soknadskandidater.forEach { sok ->
            val sykPeriode = tidsenhet.fom..tidsenhet.tom
            val sokPeriode = sok.fom!!..sok.tom!!

            // Sykmelding etter
            if (sokPeriode.overlap(sykPeriode) &&
                sokPeriode.start.isBeforeOrEqual(sykPeriode.start) &&
                sokPeriode.endInclusive.isBefore(sykPeriode.endInclusive)
            ) {
                val overlappendeTidsenhet = Tidsenhet(tidsenhet.fom, sokPeriode.endInclusive)
                ferdigsplittet.add(overlappendeTidsenhet)

                val splittbarTidsenhet = Tidsenhet(sokPeriode.endInclusive.plusDays(1), tidsenhet.tom)
                tidsenhet = splittbarTidsenhet

                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_INNI,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    eksisterendeSykepengesoknadId = sok.id,
                    klippet = true,
                    endringIUforegrad = EndringIUforegrad.VET_IKKE
                )
            }

            // Sykmelding før
            if (sokPeriode.overlap(sykPeriode) &&
                sokPeriode.start.isAfter(sykPeriode.start) &&
                sokPeriode.endInclusive.isAfterOrEqual(sykPeriode.endInclusive)
            ) {
                val overlappendeTidsenhet = Tidsenhet(sokPeriode.start, tidsenhet.tom)
                ferdigsplittet.add(overlappendeTidsenhet)

                val splittbarTidsenhet = Tidsenhet(tidsenhet.fom, sokPeriode.start.minusDays(1))
                tidsenhet = splittbarTidsenhet

                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_ETTER,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    eksisterendeSykepengesoknadId = sok.id,
                    klippet = true,
                    endringIUforegrad = EndringIUforegrad.VET_IKKE
                )
            }

            // Sykmelding større
            if (sokPeriode.overlap(sykPeriode) &&
                sokPeriode.start.isAfter(sykPeriode.start) &&
                sokPeriode.endInclusive.isBefore(sykPeriode.endInclusive)
            ) {
                val overlappendeTidsenhet = Tidsenhet(sokPeriode.start, sokPeriode.endInclusive)
                ferdigsplittet.add(overlappendeTidsenhet)

                val splittbarTidsenhetStart = Tidsenhet(tidsenhet.fom, sokPeriode.start.minusDays(1))
                val splittbarTidsenhetSlutt = Tidsenhet(sokPeriode.endInclusive.plusDays(1), tidsenhet.tom)
                tidsenhet = splittbarTidsenhetStart
                splittbareTidsenheter.add(splittbarTidsenhetSlutt)

                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_INNI_SLUTTER_INNI,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    eksisterendeSykepengesoknadId = sok.id,
                    klippet = true,
                    endringIUforegrad = EndringIUforegrad.VET_IKKE
                )
            }

            // Sykmelding lik eller inni
            if (sokPeriode.overlap(sykPeriode) &&
                sokPeriode.start.isBeforeOrEqual(sykPeriode.start) &&
                sokPeriode.endInclusive.isAfterOrEqual(sykPeriode.endInclusive)
            ) {
                klippMetrikk.klippMetrikk(
                    klippMetrikkVariant = KlippVariant.SYKMELDING_STARTER_FOR_SLUTTER_ETTER,
                    soknadstatus = sok.status.toString(),
                    sykmeldingId = sykmeldingId,
                    eksisterendeSykepengesoknadId = sok.id,
                    klippet = false,
                    endringIUforegrad = EndringIUforegrad.VET_IKKE
                )
            }
        }

        splittbar.add(tidsenhet)
    }
    return this
}

private fun SykmeldingTidsenheter.splittLangeSykmeldingperioder(): SykmeldingTidsenheter {
    while (splittbar.isNotEmpty()) {
        val splittbarTidsenhet = splittbar.removeFirst()
        val lengdePaaSykmelding = DAYS.between(splittbarTidsenhet.fom, splittbarTidsenhet.tom) + 1

        val antallDeler = ceil(lengdePaaSykmelding / 31.0)
        val grunnlengde = floor(lengdePaaSykmelding / antallDeler)
        var rest = lengdePaaSykmelding % grunnlengde

        var soknadFOM = splittbarTidsenhet.fom

        var i = 0
        while (i < antallDeler) {
            val lengde = grunnlengde.toInt() + if (rest-- > 0) 1 else 0
            val tidsenhet = Tidsenhet(fom = soknadFOM, tom = soknadFOM.plusDays((lengde - 1).toLong()))
            ferdigsplittet.add(tidsenhet)
            soknadFOM = tidsenhet.tom.plusDays(1)
            i++
        }
    }
    return this
}

private fun splittPeriodeBasertPaaUke(periode: Tidsenhet): List<Tidsenhet> {
    val liste = ArrayList<Tidsenhet>()

    val senesteTom = periode.tom
    var fom = periode.fom
    val lengdePaaSykmelding = antallDager(fom, senesteTom)
    val antallDeler = ceil(lengdePaaSykmelding / 28.0)

    if (antallDeler == 1.0) {
        liste.add(Tidsenhet(fom = fom, tom = senesteTom))
        return liste
    }

    val grunnlengde = floor(lengdePaaSykmelding / antallDeler)

    var lengde = 28
    if (grunnlengde <= 21) {
        lengde = 21
    }

    var tom: LocalDate
    do {
        tom = min(sammeEllerSistSondag(fom.plusDays(lengde.toLong())), senesteTom)
        if (DAYS.between(tom, senesteTom) <= 4L) {
            tom = senesteTom
        }
        liste.add(Tidsenhet(fom = fom, tom = tom))
        fom = tom.plusDays(1)
    } while (tom.isBefore(senesteTom))

    return liste
}

private fun sammeEllerSistSondag(localDate: LocalDate): LocalDate {
    var day = localDate
    while (true) {
        if (day.dayOfWeek == DayOfWeek.SUNDAY) {
            return day
        }
        day = day.minusDays(1)
    }
}
