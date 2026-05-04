package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

private val log = LoggerFactory.getLogger("OverlappUtil")

enum class EndringIUforegrad {
    FLERE_PERIODER,
    ØKT_UFØREGRAD,
    SAMME_UFØREGRAD,
    LAVERE_UFØREGRAD,
    VET_IKKE,
}

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippes(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { sykepengesoknad ->
        val eksisterendeSoknad = sykepengesoknad.sorteringstidspunkt()
        val innkommendeSykmelding = sykmeldingKafkaMessage.sykmelding.sorteringstidspunkt()
        val erEldre = eksisterendeSoknad.tidspunkt.isBefore(innkommendeSykmelding.tidspunkt)
        if (erEldre) {
            log.info(
                "Søknad ${sykepengesoknad.id} (${eksisterendeSoknad.kilde}: ${eksisterendeSoknad.tidspunkt}) er eldre enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "(${innkommendeSykmelding.kilde}: ${innkommendeSykmelding.tidspunkt}) – søknaden er kandidat for klipp",
            )
        }
        return@filter erEldre
    }

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingen(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val eksisterendeSoknad = soknad.sorteringstidspunkt()
        val innkommendeSykmelding = sykmeldingKafkaMessage.sykmelding.sorteringstidspunkt()
        val erNyere = eksisterendeSoknad.tidspunkt.isAfter(innkommendeSykmelding.tidspunkt)
        if (erNyere) {
            log.info(
                "Søknad ${soknad.id} (${eksisterendeSoknad.kilde}: ${eksisterendeSoknad.tidspunkt}) er nyere enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "(${innkommendeSykmelding.kilde}: ${innkommendeSykmelding.tidspunkt}) – sykmeldingen er kandidat for klipp",
            )
        }
        return@filter erNyere
    }

private data class Sorteringstidspunkt(
    val tidspunkt: Instant,
    val kilde: String,
)

private fun Sykepengesoknad.sorteringstidspunkt(): Sorteringstidspunkt =
    if (sykmeldingSignaturDato != null) {
        Sorteringstidspunkt(tidspunkt = sykmeldingSignaturDato, kilde = "signaturDato")
    } else {
        Sorteringstidspunkt(tidspunkt = sykmeldingSkrevet!!, kilde = "sykmeldingSkrevet")
    }

private fun ArbeidsgiverSykmeldingDTO.sorteringstidspunkt(): Sorteringstidspunkt =
    if (signaturDato != null) {
        Sorteringstidspunkt(tidspunkt = signaturDato.toInstant(), kilde = "signaturDato")
    } else {
        Sorteringstidspunkt(tidspunkt = behandletTidspunkt.toInstant(), kilde = "behandletTidspunkt")
    }

private fun SykepengesoknadDAO.alleSomOverlapper(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
): List<Sykepengesoknad> {
    val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
    val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
    return this
        .finnSykepengesoknader(identer)
        .asSequence()
        .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
        .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
        .filter { it.arbeidsgiverOrgnummer == orgnummer }
        .filter { sok ->
            val soknadPeriode = sok.fom!!..sok.tom!!
            sykmeldingPeriode.overlap(soknadPeriode)
        }.toList()
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

internal fun SykmeldingKafkaMessageDTO.periode() = sykmelding.sykmeldingsperioder.periode()

internal fun List<SykmeldingsperiodeAGDTO>.periode(): ClosedRange<LocalDate> {
    val sykmeldingFom = minOf { it.fom }
    val sykmeldingTom = maxOf { it.tom }
    return sykmeldingFom..sykmeldingTom
}

internal fun SykmeldingKafkaMessageDTO.erstattPerioder(nyePerioder: List<SykmeldingsperiodeAGDTO>) =
    copy(
        sykmelding =
            sykmelding.copy(
                sykmeldingsperioder = nyePerioder,
            ),
    )

/**
 * Så lenge de har minst en dato til felles
 */
fun ClosedRange<LocalDate>.overlap(other: ClosedRange<LocalDate>): Boolean =
    this.start in other || this.endInclusive in other || other.start in this || other.endInclusive in this
