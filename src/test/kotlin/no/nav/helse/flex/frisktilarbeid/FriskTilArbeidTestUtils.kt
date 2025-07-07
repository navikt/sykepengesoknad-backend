package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.medlemskap.tilPostgresJson
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun lagFriskTilArbeidVedtakStatus(
    fnr: String,
    status: Status,
    vedtaksperiode: Periode = Periode(fom = LocalDate.now(), tom = LocalDate.now().plusDays(13)),
): FriskTilArbeidVedtakStatus =
    FriskTilArbeidVedtakStatus(
        uuid = UUID.randomUUID().toString(),
        personident = fnr,
        begrunnelse = "Begrunnelse",
        fom = vedtaksperiode.fom,
        tom = vedtaksperiode.tom,
        status = status,
        statusAt = OffsetDateTime.now(),
        statusBy = "Test",
    )

internal fun lagFriskTilArbeidVedtakDbRecord(friskTilArbeidVedtak: FriskTilArbeidVedtakStatus): FriskTilArbeidVedtakDbRecord {
    val friskTilArbeidVedtakDbRecord =
        FriskTilArbeidVedtakDbRecord(
            vedtakUuid = UUID.randomUUID().toString(),
            key = friskTilArbeidVedtak.personident.asProducerRecordKey(),
            opprettet = Instant.now(),
            fnr = friskTilArbeidVedtak.personident,
            fom = friskTilArbeidVedtak.fom,
            tom = friskTilArbeidVedtak.tom,
            vedtak = friskTilArbeidVedtak.tilPostgresJson(),
            behandletStatus = BehandletStatus.BEHANDLET,
            ignorerArbeidssokerregister = null,
        )
    return friskTilArbeidVedtakDbRecord
}

internal fun lagFriskTilArbeidSoknad(
    fnr: String,
    friskTilArbeidVedtakId: String,
    soknadStatus: Soknadstatus,
    fom: LocalDate,
    tom: LocalDate,
): Sykepengesoknad {
    val soknad =
        Sykepengesoknad(
            id = UUID.randomUUID().toString(),
            fnr = fnr,
            soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
            status = soknadStatus,
            opprettet = Instant.now(),
            startSykeforlop = LocalDate.now(),
            fom = fom,
            tom = tom,
            arbeidssituasjon = null,
            arbeidsgiverOrgnummer = null,
            arbeidsgiverNavn = null,
            sykmeldingId = UUID.randomUUID().toString(),
            sykmeldingSkrevet = null,
            soknadPerioder = emptyList(),
            egenmeldtSykmelding = null,
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
            friskTilArbeidVedtakId = friskTilArbeidVedtakId,
        )
    return soknad
}
