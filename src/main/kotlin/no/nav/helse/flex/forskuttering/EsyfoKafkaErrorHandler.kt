package no.nav.helse.flex.forskuttering

import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.backoff.FixedBackOff

@Component
class EsyfoKafkaErrorHandler :
    DefaultErrorHandler(
        ConsumerRecordRecoverer { _, _ -> throw IllegalStateException("ESYFO-record kan ikke kvitteres etter feil") },
        FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS),
    )
