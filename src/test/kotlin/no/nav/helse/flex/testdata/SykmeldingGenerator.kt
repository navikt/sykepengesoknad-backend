package no.nav.helse.flex.testdata

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.*
import no.nav.syfo.model.sykmeldingstatus.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Deprecated("bruk ny")
fun skapArbeidsgiverSykmelding(
    sykmeldingId: String = UUID.randomUUID().toString(),
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    type: PeriodetypeDTO = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    reisetilskudd: Boolean = false,
    gradert: GradertDTO? = null,
    merknader: List<Merknad>? = null,
    behandlingsdager: Int? = null,
): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = sykmeldingId,
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = fom,
                tom = tom,
                type = type,
                reisetilskudd = reisetilskudd,
                aktivitetIkkeMulig = null,
                behandlingsdager = behandlingsdager,
                gradert = gradert,
                innspillTilArbeidsgiver = null
            )
        ),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        syketilfelleStartDato = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        behandler = BehandlerAGDTO(
            fornavn = "Lege",
            mellomnavn = null,
            etternavn = "Legesen",
            hpr = null,
            adresse = AdresseDTO(
                gate = null,
                postnummer = null,
                kommune = null,
                postboks = null,
                land = null
            ),
            tlf = null
        ),
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        prognose = null,
        papirsykmelding = false,
        merknader = merknader
    )
}

fun skapSykmeldingStatusKafkaMessageDTO(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
    statusEvent: String = STATUS_BEKREFTET,
    fnr: String,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    arbeidsgiver: ArbeidsgiverStatusDTO? = null,
    sykmeldingId: String = UUID.randomUUID().toString()

): SykmeldingStatusKafkaMessageDTO {
    return SykmeldingStatusKafkaMessageDTO(
        event = SykmeldingStatusKafkaEventDTO(
            statusEvent = statusEvent,
            sykmeldingId = sykmeldingId,
            arbeidsgiver = arbeidsgiver,
            timestamp = timestamp,
            sporsmals = listOf(
                SporsmalOgSvarDTO(
                    tekst = "Hva jobber du som?",
                    shortName = ShortNameDTO.ARBEIDSSITUASJON,
                    svartype = SvartypeDTO.ARBEIDSSITUASJON,
                    svar = arbeidssituasjon.name
                )
            )
        ),
        kafkaMetadata = KafkaMetadataDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            source = "Test",
            fnr = fnr
        )
    )
}

fun skapArbeidsgiverSykmelding(
    sykmeldingId: String = UUID.randomUUID().toString(),
    sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> = listOf(
        SykmeldingsperiodeAGDTO(
            fom = LocalDate.of(2020, 2, 1),
            tom = LocalDate.of(2020, 2, 15),
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null
        )
    ),
    merknader: List<Merknad>? = null,
): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = sykmeldingId,
        sykmeldingsperioder = sykmeldingsperioder,
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        syketilfelleStartDato = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        behandler = BehandlerAGDTO(
            fornavn = "Lege",
            mellomnavn = null,
            etternavn = "Legesen",
            hpr = null,
            adresse = AdresseDTO(
                gate = null,
                postnummer = null,
                kommune = null,
                postboks = null,
                land = null
            ),
            tlf = null
        ),
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        prognose = null,
        papirsykmelding = false,
        merknader = merknader
    )
}

fun gradertSykmeldt(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    grad: Int = 50,
): List<SykmeldingsperiodeAGDTO> {
    return listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.GRADERT,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = GradertDTO(grad = grad, reisetilskudd = false),
            innspillTilArbeidsgiver = null
        )
    )
}

fun heltSykmeldt(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
): List<SykmeldingsperiodeAGDTO> {
    return listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null
        )
    )
}

fun reisetilskudd(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
): List<SykmeldingsperiodeAGDTO> {
    return listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null
        )
    )
}

fun behandingsdager(
    fom: LocalDate = LocalDate.of(2018, 1, 1),
    tom: LocalDate = LocalDate.of(2018, 1, 10),
    behandlingsdager: Int = 1
): List<SykmeldingsperiodeAGDTO> {
    return listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = behandlingsdager,
            gradert = null,
            innspillTilArbeidsgiver = null
        )
    )
}

fun sykmeldingKafkaMessage(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
    fnr: String,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    arbeidsgiver: ArbeidsgiverStatusDTO? = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Butikken"),
    sykmeldingId: String = UUID.randomUUID().toString(),
    sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> = heltSykmeldt(
        fom = LocalDate.of(2020, 2, 1),
        tom = LocalDate.of(2020, 2, 15),
    ),
    merknader: List<Merknad>? = null,
): SykmeldingKafkaMessage {
    val faktiskArbeidsgiver = if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
        arbeidsgiver!!
    } else {
        null
    }
    val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        arbeidssituasjon = arbeidssituasjon,
        statusEvent = if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
            STATUS_SENDT
        } else {
            STATUS_BEKREFTET
        },
        arbeidsgiver = faktiskArbeidsgiver,
        sykmeldingId = sykmeldingId,
        timestamp = timestamp
    )

    val sykmelding = skapArbeidsgiverSykmelding(
        sykmeldingId = sykmeldingId,
        sykmeldingsperioder = sykmeldingsperioder,
        merknader = merknader,
    )

    return SykmeldingKafkaMessage(
        sykmelding = sykmelding,
        event = sykmeldingStatusKafkaMessageDTO.event,
        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
    )
}
