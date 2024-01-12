package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding

data class MottattSykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
)
