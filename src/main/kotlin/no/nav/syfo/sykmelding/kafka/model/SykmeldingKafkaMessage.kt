package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding

data class SykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
