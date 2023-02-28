package no.nav.helse.flex.domain

import java.time.Instant

enum class Utgiftstype {
    OFFENTLIG_TRANSPORT, TAXI, PARKERING, ANNET
}

data class Kvittering(
    val blobId: String,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Utgiftstype,
    val opprettet: Instant
)
