package no.nav.syfo.sykmelding.kafka.model

data class SykmeldingStatusKafkaMessageDTO(
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
