package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmeldingDTO

data class SykmeldingKafkaMessageDTO(
    val sykmelding: ArbeidsgiverSykmeldingDTO,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
