package no.nav.helse.flex.aktivering.kafka

data class AktiveringBestilling(
    val fnr: String,
    val soknadId: String
)
