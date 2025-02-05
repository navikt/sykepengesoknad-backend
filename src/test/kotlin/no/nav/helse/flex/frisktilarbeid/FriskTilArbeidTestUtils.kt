package no.nav.helse.flex.frisktilarbeid

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun lagFriskTilArbeidVedtakStatus(
    fnr: String,
    status: Status,
    vedtaksperiode: Vedtaksperiode =
        Vedtaksperiode(
            periodeStart = LocalDate.now(),
            periodeSlutt = LocalDate.now().plusDays(13),
        ),
): FriskTilArbeidVedtakStatus =
    FriskTilArbeidVedtakStatus(
        uuid = UUID.randomUUID().toString(),
        personident = fnr,
        begrunnelse = "Begrunnelse",
        fom = vedtaksperiode.periodeStart,
        tom = vedtaksperiode.periodeSlutt,
        status = status,
        statusAt = OffsetDateTime.now(),
        statusBy = "Test",
    )

internal data class Vedtaksperiode(
    val periodeStart: LocalDate,
    val periodeSlutt: LocalDate,
)
