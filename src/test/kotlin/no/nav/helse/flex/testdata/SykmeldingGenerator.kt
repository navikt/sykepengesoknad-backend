package no.nav.helse.flex.testdata

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.sykmelding.Gradert
import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.*
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

fun skapArbeidsgiverSykmelding(
    sykmeldingId: String = UUID.randomUUID().toString(),
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    type: PeriodetypeDTO = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    reisetilskudd: Boolean = false,
    gradert: GradertDTO? = null,
    merknader: List<Merknad>? = null,
    behandlingsdager: Int? = null,
): ArbeidsgiverSykmeldingDTO =
    ArbeidsgiverSykmeldingDTO(
        id = sykmeldingId,
        sykmeldingsperioder =
            listOf(
                SykmeldingsperiodeAGDTO(
                    fom = fom,
                    tom = tom,
                    type = type,
                    reisetilskudd = reisetilskudd,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = behandlingsdager,
                    gradert = gradert,
                    innspillTilArbeidsgiver = null,
                ),
            ),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        signaturDato = OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        syketilfelleStartDato = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        behandler =
            BehandlerAGDTO(
                fornavn = "Lege",
                mellomnavn = null,
                etternavn = "Legesen",
                hpr = null,
                adresse =
                    AdresseDTO(
                        gate = null,
                        postnummer = null,
                        kommune = null,
                        postboks = null,
                        land = null,
                    ),
                tlf = null,
            ),
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        prognose = null,
        papirsykmelding = false,
        merknader = merknader,
        utenlandskSykmelding = null,
    )

fun skapArbeidsgiverSykmelding(
    sykmeldingId: String = UUID.randomUUID().toString(),
    sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>? =
        lagSykmeldingsPerioder(
            fom = LocalDate.of(2020, 2, 1),
            tom = LocalDate.of(2020, 2, 15),
        ),
    merknader: List<Merknad>? = null,
    utenlandskSykemelding: UtenlandskSykmeldingAGDTO? = null,
    sykmeldingSkrevet: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    signaturDato: OffsetDateTime = sykmeldingSkrevet,
    syketilfelleStartDato: LocalDate? = null,
    erPapirsykmelding: Boolean? = false,
    mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    kontaktDato: LocalDate? = null,
): ArbeidsgiverSykmeldingDTO =
    ArbeidsgiverSykmeldingDTO(
        id = sykmeldingId,
        sykmeldingsperioder = sykmeldingsperioder ?: emptyList(),
        behandletTidspunkt = sykmeldingSkrevet,
        signaturDato = signaturDato,
        mottattTidspunkt = mottattTidspunkt,
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        syketilfelleStartDato = syketilfelleStartDato,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        behandler =
            BehandlerAGDTO(
                fornavn = "Lege",
                mellomnavn = null,
                etternavn = "Legesen",
                hpr = null,
                adresse =
                    AdresseDTO(
                        gate = null,
                        postnummer = null,
                        kommune = null,
                        postboks = null,
                        land = null,
                    ),
                tlf = null,
            ),
        kontaktMedPasient = KontaktMedPasientAGDTO(kontaktDato),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        prognose = null,
        papirsykmelding = erPapirsykmelding ?: false,
        merknader = merknader,
        utenlandskSykmelding = utenlandskSykemelding,
    )

fun skapArbeidsgiverSykmeldingTilSoknadOpprettelse(
    sykmeldingId: String = UUID.randomUUID().toString(),
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    type: Sykmeldingstype = Sykmeldingstype.AKTIVITET_IKKE_MULIG,
    reisetilskudd: Boolean = false,
    gradert: Gradert? = null,
    merknader: List<no.nav.helse.flex.domain.Merknad>? = null,
    eventTimestamp: OffsetDateTime = OffsetDateTime.now(),
): SykmeldingTilSoknadOpprettelse =
    SykmeldingTilSoknadOpprettelse(
        sykmeldingId = sykmeldingId,
        sykmeldingsperioder =
            listOf(
                Sykmeldingsperiode(
                    fom = fom,
                    tom = tom,
                    type = type,
                    gradert = gradert,
                    reisetilskudd = reisetilskudd,
                ),
            ),
        eventTimestamp = eventTimestamp,
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).toInstant(),
        signaturDato = OffsetDateTime.now(ZoneOffset.UTC).toInstant(),
        erUtlandskSykmelding = false,
        brukerHarOppgittForsikring = false,
        egenmeldt = false,
        egenmeldingsdagerFraSykmelding = null,
        meldingTilNavDagerFraSykmelding = null,
        fiskerBlad = null,
        merknader = merknader,
        arbeidsgiverOrgnummer = null,
        arbeidsgiverNavn = null,
        tidligereArbeidsgiverOrgnummer = null,
        erPapirsykmelding = false,
    )

fun skapSykmeldingStatusKafkaMessageDTO(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
    statusEvent: String = STATUS_BEKREFTET,
    fnr: String,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    sykmeldingId: String = UUID.randomUUID().toString(),
    tidligereArbeidsgiverOrgnummer: String? = null,
): SykmeldingStatusKafkaMessageDTO =
    SykmeldingStatusKafkaMessageDTO(
        event =
            SykmeldingStatusKafkaEventDTO(
                statusEvent = statusEvent,
                sykmeldingId = sykmeldingId,
                arbeidsgiver = arbeidsgiver,
                timestamp = timestamp,
                sporsmals =
                    listOf(
                        SporsmalOgSvarKafkaDTO(
                            tekst = "Hva jobber du som?",
                            shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
                            svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
                            svar =
                                if (listOf(Arbeidssituasjon.FISKER, Arbeidssituasjon.JORDBRUKER).contains(
                                        arbeidssituasjon,
                                    )
                                ) {
                                    Arbeidssituasjon.NAERINGSDRIVENDE.name
                                } else {
                                    arbeidssituasjon.name
                                },
                        ),
                    ),
                brukerSvar =
                    when (arbeidssituasjon) {
                        Arbeidssituasjon.FISKER -> lagFiskerInnsendtSkjemaSvar(arbeidssituasjon)
                        else -> lagKomplettInnsendtSkjemaSvar(arbeidssituasjon)
                    },
            ).let {
                if (tidligereArbeidsgiverOrgnummer != null) {
                    it.copy(tidligereArbeidsgiver = TidligereArbeidsgiverKafkaDTO("", tidligereArbeidsgiverOrgnummer, ""))
                } else {
                    it
                }
            },
        kafkaMetadata =
            KafkaMetadataDTO(
                sykmeldingId = sykmeldingId,
                timestamp = timestamp,
                source = "Test",
                fnr = fnr,
            ),
    )

fun lagKomplettInnsendtSkjemaSvar(
    arbeidssituasjon: Arbeidssituasjon,
    fiskerSvar: FiskereSvarKafkaDTO? = null,
): KomplettInnsendtSkjemaSvar =
    KomplettInnsendtSkjemaSvar(
        erOpplysningeneRiktige = SporsmalSvar("Sporsmal", JaEllerNei.JA),
        uriktigeOpplysninger = null,
        arbeidssituasjon =
            SporsmalSvar(
                "Arbeidssituasjon",
                no.nav.syfo.sykmelding.kafka.model.Arbeidssituasjon
                    .valueOf(arbeidssituasjon.name),
            ),
        arbeidsgiverOrgnummer = null,
        riktigNarmesteLeder = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder =
            SporsmalSvar(
                sporsmaltekst = "MeldingTilNavDager",
                svar = listOf(Egenmeldingsperiode(LocalDate.of(2020, 2, 1), LocalDate.of(2020, 2, 15))),
            ),
        brukerHarOppgittForsikring = null,
        egenmeldingsdager = null,
        harBruktEgenmeldingsdager = null,
        fisker = fiskerSvar,
    )

fun lagFiskerInnsendtSkjemaSvar(arbeidssituasjon: Arbeidssituasjon): KomplettInnsendtSkjemaSvar {
    val fiskerSvar =
        FiskereSvarKafkaDTO(
            blad = SporsmalSvar("Hvilket blad?", Blad.A),
            lottOgHyre = SporsmalSvar("Lott eller hyre?", LottOgHyre.LOTT),
        )

    return lagKomplettInnsendtSkjemaSvar(arbeidssituasjon, fiskerSvar)
}

fun lagSykmeldingsPerioder(
    fom: LocalDate,
    tom: LocalDate,
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null,
        ),
    )

fun gradertSykmeldt(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    grad: Int = 50,
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.GRADERT,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = GradertDTO(grad = grad, reisetilskudd = false),
            innspillTilArbeidsgiver = null,
        ),
    )

fun heltSykmeldt(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null,
        ),
    )

fun reisetilskudd(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null,
        ),
    )

fun gradertReisetilskudd(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
    grad: Int = 50,
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.GRADERT,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = GradertDTO(grad, true),
            innspillTilArbeidsgiver = null,
        ),
    )

fun avventende(
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15),
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.AVVENTENDE,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null,
        ),
    )

fun behandingsdager(
    fom: LocalDate = LocalDate.of(2018, 1, 1),
    tom: LocalDate = LocalDate.of(2018, 1, 10),
    behandlingsdager: Int = 1,
): List<SykmeldingsperiodeAGDTO> =
    listOf(
        SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.BEHANDLINGSDAGER,
            reisetilskudd = false,
            aktivitetIkkeMulig = null,
            behandlingsdager = behandlingsdager,
            gradert = null,
            innspillTilArbeidsgiver = null,
        ),
    )

fun sykmeldingKafkaMessage(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
    fnr: String,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "Butikken"),
    sykmeldingId: String = UUID.randomUUID().toString(),
    sykmeldingsperioder: List<SykmeldingsperiodeAGDTO> =
        heltSykmeldt(
            fom = LocalDate.of(2020, 2, 1),
            tom = LocalDate.of(2020, 2, 15),
        ),
    merknader: List<Merknad>? = null,
    utenlandskSykemelding: UtenlandskSykmeldingAGDTO? = null,
    sykmeldingSkrevet: OffsetDateTime = timestamp,
    signaturDato: OffsetDateTime = sykmeldingSkrevet,
    erPapirsykmelding: Boolean = false,
    tidligereArbeidsgiverOrgnummer: String? = null,
    status: String? = null,
    syketilfelleStartDato: LocalDate? = null,
    mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    kontaktDato: LocalDate? = null,
): SykmeldingKafkaMessageDTO {
    val faktiskArbeidsgiver =
        if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
            arbeidsgiver!!
        } else {
            null
        }
    val statusEvent =
        status
            ?: if (arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER) {
                STATUS_SENDT
            } else {
                STATUS_BEKREFTET
            }

    val sykmeldingStatusKafkaMessageDTO =
        skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = arbeidssituasjon,
            statusEvent = statusEvent,
            arbeidsgiver = faktiskArbeidsgiver,
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer,
        )

    val sykmelding =
        skapArbeidsgiverSykmelding(
            sykmeldingId = sykmeldingId,
            sykmeldingsperioder = sykmeldingsperioder,
            merknader = merknader,
            utenlandskSykemelding = utenlandskSykemelding,
            sykmeldingSkrevet = sykmeldingSkrevet,
            signaturDato = signaturDato,
            syketilfelleStartDato = syketilfelleStartDato,
            erPapirsykmelding = erPapirsykmelding,
            mottattTidspunkt = mottattTidspunkt,
            kontaktDato = kontaktDato,
        )

    return SykmeldingKafkaMessageDTO(
        sykmelding = sykmelding,
        event = sykmeldingStatusKafkaMessageDTO.event,
        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
    )
}
