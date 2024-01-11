package no.nav.syfo.sykmelding.kafka.model.sykmeldingstatus

import java.time.OffsetDateTime

data class KafkaMetadataDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val fnr: String,
    val source: String,
)
