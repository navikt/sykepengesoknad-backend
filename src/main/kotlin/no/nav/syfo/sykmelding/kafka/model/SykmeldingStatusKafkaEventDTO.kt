package no.nav.syfo.sykmelding.kafka.model

import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    val sporsmals: List<SporsmalOgSvarKafkaDTO>? = null,
    val erSvarOppdatering: Boolean? = null,
    val tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO? = null,
    val brukerSvar: KomplettInnsendtSkjemaSvar?,
)
