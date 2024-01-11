package no.nav.syfo.sykmelding.kafka.model.sykmeldingstatus

import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val sporsmals: List<SporsmalOgSvarDTO>? = null,
    val arbeidsgiver: ArbeidsgiverStatusDTO? = null,
    val erSvarOppdatering: Boolean? = null,
    val tidligereArbeidsgiver: no.nav.syfo.sykmelding.kafka.model.sykmelding.model.TidligereArbeidsgiverDTO? = null,
)
