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
internal const val SYKMELDING_ID_FOR_NY_LOGIKK = "f16f3712-bef2-4fad-97a2-3d1685960d97"

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
): List<Sykepengesoknad> {
    val nyttResultat = soknadKandidaterSomKanKlippesNy(orgnummer, sykmeldingKafkaMessage, identer)
    val gammeltResultat = soknadKandidaterSomKanKlippesGammel(orgnummer, sykmeldingKafkaMessage, identer)

    log.info(
        "Søknader som kan klippes for sykmelding ${sykmeldingKafkaMessage.sykmelding.id}: " +
            "Ny logikk: ${nyttResultat.map { it.id }}. " +
            "Gammel logikk: ${gammeltResultat.map { it.id }}.",
    )
    return if (skalBrukeNyLogikk(sykmeldingKafkaMessage.sykmelding.id)) {
        nyttResultat
    } else {
        gammeltResultat
    }
}

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippesNy(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val eksisterendeSoknad = soknad.signaturDatoNyLogikk()
        val innkommendeSykmelding = sykmeldingKafkaMessage.signaturDatoNyLogikk()
        val erEldre = eksisterendeSoknad.isBefore(innkommendeSykmelding)
        if (erEldre) {
            log.info(
                "Søknad ${soknad.id} ($eksisterendeSoknad) er eldre enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "($innkommendeSykmelding) – søknaden er kandidat for klipp " +
                    "(logikk: ny)",
            )
        }
        return@filter erEldre
    }

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippesGammel(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { sykepengesoknad ->
        val erEldre = sykepengesoknad.erEldreMainLogikk(sykmeldingKafkaMessage.sykmelding)
        if (erEldre) {
            log.info(
                "Søknad ${sykepengesoknad.id} (sykmeldingSkrevet: ${sykepengesoknad.sykmeldingSkrevet}) er eldre enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "(behandletTidspunkt: ${sykmeldingKafkaMessage.sykmelding.behandletTidspunkt}) – søknaden er kandidat for klipp " +
                    "(logikk: main)",
            )
        }
        return@filter erEldre
    }

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingen(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
): List<Sykepengesoknad> {
    val nyttResultat =
        soknadKandidaterSomKanKlippeSykmeldingenNy(orgnummer, sykmeldingKafkaMessage, identer)
    val gammeltResultat =
        soknadKandidaterSomKanKlippeSykmeldingenGammel(orgnummer, sykmeldingKafkaMessage, identer)

    log.info(
        "Søknader som kan klippe sykmelding ${sykmeldingKafkaMessage.sykmelding.id}: " +
            "Ny logikk: ${nyttResultat.map { it.id }}. " +
            "Gammel logikk: ${gammeltResultat.map { it.id }}.",
    )
    return if (skalBrukeNyLogikk(sykmeldingKafkaMessage.sykmelding.id)) {
        nyttResultat
    } else {
        gammeltResultat
    }
}

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingenNy(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val eksisterendeSoknad = soknad.signaturDatoNyLogikk()
        val innkommendeSykmelding = sykmeldingKafkaMessage.signaturDatoNyLogikk()
        val erNyere = eksisterendeSoknad.isAfter(innkommendeSykmelding)
        if (erNyere) {
            log.info(
                "Søknad ${soknad.id} ($eksisterendeSoknad) er nyere enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "($innkommendeSykmelding) – sykmeldingen er kandidat for klipp " +
                    "(logikk: ny)",
            )
        }
        return@filter erNyere
    }

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingenGammel(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val erNyere = soknad.erNyereMainLogikk(sykmeldingKafkaMessage.sykmelding)
        if (erNyere) {
            log.info(
                "Søknad ${soknad.id} (sykmeldingSkrevet: ${soknad.sykmeldingSkrevet}) er nyere enn " +
                    "innkommende sykmelding ${sykmeldingKafkaMessage.sykmelding.id} " +
                    "(behandletTidspunkt: ${sykmeldingKafkaMessage.sykmelding.behandletTidspunkt}) " +
                    "– sykmeldingen er kandidat for klipp (logikk: main)",
            )
        }
        return@filter erNyere
    }

private fun skalBrukeNyLogikk(sykmeldingId: String): Boolean = sykmeldingId == SYKMELDING_ID_FOR_NY_LOGIKK

private fun Sykepengesoknad.signaturDatoNyLogikk(): Instant =
    this.sykmeldingSignaturDato
        ?: throw RuntimeException("Søknad ${this.id} mangler signaturDato, og kan derfor ikke klippes i den nye logikken")

private fun SykmeldingKafkaMessageDTO.signaturDatoNyLogikk(): Instant =
    this.sykmelding.signaturDato?.toInstant()
        ?: throw RuntimeException(
            "Sykmelding ${this.sykmelding.id} mangler signaturDato, og kan derfor ikke klippes i den nye logikken",
        )

// Main-logikken er basert på sykmeldingSkrevet og behandletTidspunkt, og signaturDato brukes kun som tiebreaker når disse er like
private fun Sykepengesoknad.erEldreMainLogikk(sykmelding: ArbeidsgiverSykmeldingDTO): Boolean =
    sykmeldingSkrevet!!.isBefore(sykmelding.behandletTidspunkt.toInstant()) || signaturDatoIsBefore(sykmelding)

private fun Sykepengesoknad.erNyereMainLogikk(sykmelding: ArbeidsgiverSykmeldingDTO): Boolean =
    sykmeldingSkrevet!!.isAfter(sykmelding.behandletTidspunkt.toInstant()) || signaturDatoIsAfter(sykmelding)

private fun Sykepengesoknad.signaturDatoIsBefore(sykmelding: ArbeidsgiverSykmeldingDTO): Boolean =
    when {
        this.sykmeldingSkrevet != sykmelding.behandletTidspunkt.toInstant() -> false
        this.sykmeldingSignaturDato == null || sykmelding.signaturDato == null -> false
        this.sykmeldingSignaturDato.isBefore(sykmelding.signaturDato.toInstant()) -> true
        else -> false
    }

private fun Sykepengesoknad.signaturDatoIsAfter(sykmelding: ArbeidsgiverSykmeldingDTO): Boolean =
    when {
        this.sykmeldingSkrevet != sykmelding.behandletTidspunkt.toInstant() -> false
        this.sykmeldingSignaturDato == null || sykmelding.signaturDato == null -> false
        this.sykmeldingSignaturDato.isAfter(sykmelding.signaturDato.toInstant()) -> true
        else -> false
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
