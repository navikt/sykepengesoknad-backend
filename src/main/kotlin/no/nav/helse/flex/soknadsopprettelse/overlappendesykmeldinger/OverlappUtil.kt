package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

private val log = LoggerFactory.getLogger("no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger.OverlappUtil")

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
    arbeidssituasjon: Arbeidssituasjon,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer, arbeidssituasjon)
    .filter { soknad ->
        val eksisterendeSoknad = soknad.datoForKlippSammenligning()
        val innkommendeSykmelding = sykmeldingKafkaMessage.datoForKlippSammenligning()
        val soknadErUtdatert = eksisterendeSoknad.isBefore(innkommendeSykmelding)
        if (soknadErUtdatert) {
            log.info(
                "SIGNATURDATO_NY: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er nyere enn sykmeldingen brukt for å opprette søknad ${soknad.id} – søknaden er utdatert og kandidat for klipp",
            )
        }
        return@filter soknadErUtdatert
    }

internal fun SykepengesoknadDAO.soknadKandidaterSomKanKlippeSykmeldingen(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
    arbeidssituasjon: Arbeidssituasjon,
) = alleSomOverlapper(orgnummer, sykmeldingKafkaMessage, identer, arbeidssituasjon)
    .filter { soknad ->
        val eksisterendeSoknad = soknad.datoForKlippSammenligning()
        val innkommendeSykmelding = sykmeldingKafkaMessage.datoForKlippSammenligning()
        val sykmeldingErUtdatert = eksisterendeSoknad.isAfter(innkommendeSykmelding)
        if (sykmeldingErUtdatert) {
            log.info(
                "SIGNATURDATO_NY: Sykmelding ${sykmeldingKafkaMessage.sykmelding.id} er eldre enn sykmeldingen brukt for å opprette søknad ${soknad.id} – sykmeldingen er utdatert og kandidat for klipp",
            )
        }
        return@filter sykmeldingErUtdatert
    }

fun Sykepengesoknad.datoForKlippSammenligning(): Instant =
    this.sykmeldingSignaturDato ?: this.sykmeldingSkrevet!!.also {
        log.info("Klippsammenligning: Sykepengesoknad ${this.id} mangler signaturDato, bruker behandletTidspunkt")
    }

fun SykmeldingKafkaMessageDTO.datoForKlippSammenligning(): Instant =
    this.sykmelding.signaturDato?.toInstant() ?: this.sykmelding.behandletTidspunkt.toInstant().also {
        log.info("Klippsammenligning: SykmeldingKafkaMessage ${this.sykmelding.id} mangler signaturDato, bruker behandletTidspunkt")
    }

fun SykmeldingTilSoknadOpprettelse.datoForKlippSammenligning(): Instant =
    this.signaturDato ?: this.behandletTidspunkt.also {
        log.info("Klippsammenligning: SykmeldingTilSoknadOpprettelse ${this.sykmeldingId} mangler signaturDato, bruker behandletTidspunkt")
    }

private fun SykepengesoknadDAO.alleSomOverlapper(
    orgnummer: String?,
    sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    identer: FolkeregisterIdenter,
    arbeidssituasjon: Arbeidssituasjon,
): List<Sykepengesoknad> {
    val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
    val sykmeldingPeriode = sykmeldingKafkaMessage.periode()
    return this
        .finnSykepengesoknader(identer)
        .filterNot { it.sykmeldingId == sykmeldingId } // Korrigerte sykmeldinger håndteres her SlettSoknaderTilKorrigertSykmeldingService
        .filtrerArbeidssituasjon(arbeidssituasjon, orgnummer)
        .filter { sok ->
            val soknadPeriode = sok.fom!!..sok.tom!!
            sykmeldingPeriode.overlap(soknadPeriode)
        }
}

private fun List<Sykepengesoknad>.filtrerArbeidssituasjon(
    arbeidssituasjon: Arbeidssituasjon,
    orgnummer: String?,
): List<Sykepengesoknad> = this.filter { it.matcherArbeidssituasjon(arbeidssituasjon, orgnummer) }

internal fun Sykepengesoknad.matcherArbeidssituasjon(
    arbeidssituasjon: Arbeidssituasjon,
    orgnummer: String?,
): Boolean =
    when (arbeidssituasjon) {
        Arbeidssituasjon.ARBEIDSTAKER -> {
            this.soknadstype == Soknadstype.ARBEIDSTAKERE && arbeidsgiverOrgnummer == orgnummer
        }

        Arbeidssituasjon.NAERINGSDRIVENDE -> {
            this.soknadstype == Soknadstype.SELVSTENDIGE_OG_FRILANSERE &&
                this.arbeidssituasjon == Arbeidssituasjon.NAERINGSDRIVENDE
        }

        else -> {
            throw RuntimeException("Ugyldig arbeidssituasjon for klipp")
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
