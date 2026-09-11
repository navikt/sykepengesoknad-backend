package no.nav.helse.flex.aktivering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.exception.AdvisoryLockConflictException
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_AKTIVERING_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AktiveringConsumer(
    private val soknadAktivering: SoknadAktivering,
    private val retryLogger: RetryLogger,
    private val environmentToggles: EnvironmentToggles,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [SYKEPENGESOKNAD_AKTIVERING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "sykepengesoknad-aktivering",
        idIsGroup = false,
        concurrency = "4",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            soknadAktivering.aktiverSoknad(cr.key())
        } catch (_: AdvisoryLockConflictException) {
            log.info("Utsetter aktivering av søknad ${cr.key()}. Personen behandles av en annen prosess.")
            acknowledgment.nack(Duration.ofSeconds(1))
            return
        } catch (e: Exception) {
            if (environmentToggles.isNotProduction()) {
                val soknad = sykepengesoknadDAO.finnSykepengesoknad(cr.key())

                when (soknad.soknadstype) {
                    Soknadstype.SELVSTENDIGE_OG_FRILANSERE -> {
                        sykepengesoknadDAO.slettSoknad(soknad.id)
                        log.warn(
                            "Feilet ved aktivering av ${soknad.soknadstype} søknad: ${soknad.id} i DEV. Setter søknaden til ${Soknadstatus.SLETTET}.",
                            e,
                        )
                    }
                    else -> {
                        log.warn(
                            "Feilet ved aktivering av ${soknad.soknadstype} søknad: ${soknad.id} i DEV.",
                            e,
                        )
                    }
                }
            } else {
                val warnEllerErrorLogger = retryLogger.inkrementerRetriesOgReturnerLogger(cr.key())
                warnEllerErrorLogger.log(
                    "Feilet ved aktivering av søknad ${cr.key()}.",
                    e,
                )
            }
        }
        // Hvis søknaden har feilet så vil den bli forsøkt aktivert igjen av AktiveringJobb.
        acknowledgment.acknowledge()
    }
}
