package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import java.time.LocalDate

enum class EndringIUforegrad {
    FLERE_PERIODER,
    ØKT_UFØREGRAD,
    SAMME_UFØREGRAD,
    LAVERE_UFØREGRAD,
    VET_IKKE,
}

internal fun SykepengesoknadDAO.soknadKandidater(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    identer: FolkeregisterIdenter,
): List<Sykepengesoknad> {
    val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
    val behandletTidspunkt = sykmeldingKafkaMessage.sykmelding.behandletTidspunkt.toInstant()
    val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
    return this.finnSykepengesoknader(identer)
        .asSequence()
        .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
        .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
        .filter { it.sykmeldingSkrevet!!.isBefore(behandletTidspunkt) }
        .filter { it.arbeidsgiverOrgnummer == orgnummer }
        .filter { sok ->
            val soknadPeriode = sok.fom!!..sok.tom!!
            sykmeldingPeriode.overlap(soknadPeriode)
        }
        .toList()
}

internal fun finnEndringIUforegrad(
    tidligerePerioder: List<Soknadsperiode>?,
    nyePerioder: List<Soknadsperiode>,
): EndringIUforegrad {
    if (tidligerePerioder == null || tidligerePerioder.size > 1 || nyePerioder.size > 1) {
        return EndringIUforegrad.FLERE_PERIODER
    }
    if (nyePerioder.first().grad > tidligerePerioder.first().grad) {
        return EndringIUforegrad.ØKT_UFØREGRAD
    }
    if (nyePerioder.first().grad == tidligerePerioder.first().grad) {
        return EndringIUforegrad.SAMME_UFØREGRAD
    }
    if (nyePerioder.first().grad < tidligerePerioder.first().grad) {
        return EndringIUforegrad.LAVERE_UFØREGRAD
    }
    return EndringIUforegrad.VET_IKKE
}

internal fun SykmeldingKafkaMessage.periode() = sykmelding.sykmeldingsperioder.periode()

internal fun List<SykmeldingsperiodeAGDTO>.periode(): ClosedRange<LocalDate> {
    val sykmeldingFom = minOf { it.fom }
    val sykmeldingTom = maxOf { it.tom }
    return sykmeldingFom..sykmeldingTom
}

internal fun SykmeldingKafkaMessage.erstattPerioder(nyePerioder: List<SykmeldingsperiodeAGDTO>) = copy(
    sykmelding = sykmelding.copy(
        sykmeldingsperioder = nyePerioder
    )
)

/**
 * Så lenge de har minst en dato til felles
 */
fun ClosedRange<LocalDate>.overlap(other: ClosedRange<LocalDate>): Boolean {
    return this.start in other || this.endInclusive in other || other.start in this || other.endInclusive in this
}
