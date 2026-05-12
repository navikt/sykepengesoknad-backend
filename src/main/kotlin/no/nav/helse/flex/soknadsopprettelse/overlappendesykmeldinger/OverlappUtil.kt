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

    loggDryRunNyLogikk(
        kontekst = "KLIPP_SØKNAD",
        sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
        nyttResultat = nyttResultat,
        gammeltResultat = gammeltResultat,
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
        val soknadErUtdatert = eksisterendeSoknad.isBefore(innkommendeSykmelding)
        if (soknadErUtdatert) {
            log.info(
                "SIGNATURDATO_NY: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er nyere enn sykmeldingen brukt for å opprette søknad ${soknad.id} – søknaden er utdatert og kandidat for klipp",
            )
        }
        return@filter soknadErUtdatert
    }

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippesGammel(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val soknadErUtdatert = soknad.erEldreMainLogikk(sykmeldingKafkaMessage.sykmelding)
        if (soknadErUtdatert) {
            log.info(
                "SIGNATURDATO_GAMMEL: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er nyere enn sykmeldingen brukt for å opprette søknad ${soknad.id} – søknaden er utdatert og kandidat for klipp",
            )
        }
        return@filter soknadErUtdatert
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

    loggDryRunNyLogikk(
        kontekst = "KLIPP_SYKMELDING",
        sykmeldingId = sykmeldingKafkaMessage.sykmelding.id,
        nyttResultat = nyttResultat,
        gammeltResultat = gammeltResultat,
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
        val sykmeldingErUtdatert = eksisterendeSoknad.isAfter(innkommendeSykmelding)
        if (sykmeldingErUtdatert) {
            log.info(
                "SIGNATURDATO_NY: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er eldre enn sykmeldingen brukt for å opprette søknad ${soknad.id} – sykmeldingen er utdatert og kandidat for klipp",
            )
        }
        return@filter sykmeldingErUtdatert
    }

private fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingenGammel(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer)
    .filter { soknad ->
        val sykmeldingErUtdatert = soknad.erNyereMainLogikk(sykmeldingKafkaMessage.sykmelding)
        if (sykmeldingErUtdatert) {
            log.info(
                "SIGNATURDATO_GAMMEL: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er eldre enn sykmeldingen brukt for å opprette søknad ${soknad.id} – sykmeldingen er utdatert og kandidat for klipp",
            )
        }
        return@filter sykmeldingErUtdatert
    }

private fun skalBrukeNyLogikk(sykmeldingId: String): Boolean {
    val brukerNyLogikk = (sykmeldingId == SYKMELDING_ID_FOR_NY_LOGIKK)
    if (brukerNyLogikk) {
        log.info("Logikk for klipp er basert på SIGNATURDATO_NY")
    } else {
        log.info("Logikk for klipp er basert på SIGNATURDATO_GAMMEL")
    }
    return brukerNyLogikk
}

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

private fun loggDryRunNyLogikk(
    kontekst: String,
    sykmeldingId: String,
    nyttResultat: List<Sykepengesoknad>,
    gammeltResultat: List<Sykepengesoknad>,
) {
    val nyIds = nyttResultat.map { it.id }.toSet()
    val gammelIds = gammeltResultat.map { it.id }.toSet()

    val kunINy = nyIds - gammelIds
    val kunIGammel = gammelIds - nyIds

    for (id in kunINy) {
        log.info(
            "SIGNATURDATO_NY: kontekst=$kontekst sykmeldingId=$sykmeldingId soknadId=$id. Ny logikk fant søknad som kandidat for klipp.",
        )
    }
    for (id in kunIGammel) {
        log.info(
            "SIGNATURDATO_GAMMEL: kontekst=$kontekst sykmeldingId=$sykmeldingId soknadId=$id. Gammel logikk fant søknad som kandidat for klipp.",
        )
    }
}
