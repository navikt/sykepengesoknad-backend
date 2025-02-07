package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Periode
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
