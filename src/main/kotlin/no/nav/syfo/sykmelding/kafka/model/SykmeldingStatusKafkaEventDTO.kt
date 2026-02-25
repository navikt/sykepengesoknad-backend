package no.nav.syfo.sykmelding.kafka.model

import no.nav.helse.flex.domain.sykmelding.BrukersituasjonDto
import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val brukersituasjon: BrukersituasjonDto? = null,
    // TODO: Fjern
    val arbeidsgiver: ArbeidsgiverStatusKafkaDTO? = null,
    // TODO: Fjern
    val sporsmals: List<SporsmalOgSvarKafkaDTO>? = null,
    // TODO: Fjern
    val erSvarOppdatering: Boolean? = null,
    // TODO: Fjern
    val tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO? = null,
    // TODO: Fjern
    val brukerSvar: KomplettInnsendtSkjemaSvar?,
)
