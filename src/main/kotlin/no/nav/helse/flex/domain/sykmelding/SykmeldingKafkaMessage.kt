package no.nav.helse.flex.domain.sykmelding

import no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO

data class SykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
