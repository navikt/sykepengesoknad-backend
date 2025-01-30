package no.nav.helse.flex.frisktilarbeid

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun lagFriskTilArbeidVedtakStatus(
    fnr: String,
    status: Status,
): FriskTilArbeidVedtakStatus =
    FriskTilArbeidVedtakStatus(
        uuid = UUID.randomUUID().toString(),
        personident = fnr,
        begrunnelse = "Begrunnelse",
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        status = status,
        statusAt = OffsetDateTime.now(),
        statusBy = "Test",
    )
