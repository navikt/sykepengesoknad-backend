package no.nav.syfo.sykmelding.kafka.model.sykmeldingstatus

data class SykmeldingStatusKafkaMessageDTO(
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
