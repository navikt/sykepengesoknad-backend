package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import java.time.LocalDate

enum class EndringIUforegrad {
    FLERE_PERIODER,
    ØKT_UFØREGRAD,
    SAMME_UFØREGRAD,
    LAVERE_UFØREGRAD,
    VET_IKKE,
}

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippes(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer).filter {
    it.sykmeldingSkrevet!!.isBefore(sykmeldingKafkaMessage.sykmelding.behandletTidspunkt.toInstant()) ||
        it.signaturDatoIsBefore(sykmeldingKafkaMessage.sykmelding)
}

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingen(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer).filter {
    it.sykmeldingSkrevet!!.isAfter(sykmeldingKafkaMessage.sykmelding.behandletTidspunkt.toInstant()) ||
        it.signaturDatoIsAfter(sykmeldingKafkaMessage.sykmelding)
}

private fun SykepengesoknadDAO.alleSomOverlapper(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    identer: FolkeregisterIdenter,
): List<Sykepengesoknad> {
    val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
    val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
    return this.finnSykepengesoknader(identer)
        .asSequence()
        .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
        .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
        .filter { it.arbeidsgiverOrgnummer == orgnummer }
        .filter { sok ->
            val soknadPeriode = sok.fom!!..sok.tom!!
            sykmeldingPeriode.overlap(soknadPeriode)
        }
        .toList()
}

private fun Sykepengesoknad.signaturDatoIsBefore(sykmelding: ArbeidsgiverSykmelding): Boolean {
    return when {
        this.sykmeldingSkrevet != sykmelding.behandletTidspunkt.toInstant() -> false
        this.sykmeldingSignaturDato == null || sykmelding.signaturDato == null -> false
        this.sykmeldingSignaturDato.isBefore(sykmelding.signaturDato!!.toInstant()) -> true
        else -> false
    }
}

private fun Sykepengesoknad.signaturDatoIsAfter(sykmelding: ArbeidsgiverSykmelding): Boolean {
    return when {
        this.sykmeldingSkrevet != sykmelding.behandletTidspunkt.toInstant() -> false
        this.sykmeldingSignaturDato == null || sykmelding.signaturDato == null -> false
        this.sykmeldingSignaturDato.isAfter(sykmelding.signaturDato!!.toInstant()) -> true
        else -> false
    }
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

internal fun SykmeldingKafkaMessage.erstattPerioder(nyePerioder: List<SykmeldingsperiodeAGDTO>) =
    copy(
        sykmelding =
            sykmelding.copy(
                sykmeldingsperioder = nyePerioder,
            ),
    )

/**
 * Så lenge de har minst en dato til felles
 */
fun ClosedRange<LocalDate>.overlap(other: ClosedRange<LocalDate>): Boolean {
    return this.start in other || this.endInclusive in other || other.start in this || other.endInclusive in this
}
