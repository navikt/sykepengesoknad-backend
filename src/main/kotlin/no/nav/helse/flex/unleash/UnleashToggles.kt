package no.nav.helse.flex.unleash

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_TILKOMMEN_INNTEKT = "sykepengesoknad-backend-tilkommen-inntekt"
const val UNLEASH_CONTEXT_SIGRUN_PAA_KAFKA = "sykepengesoknad-backend-sigrun-paa-kafka"
const val UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET = "sykepengesoknad-backend-opphold-i-utlandet"
const val UNLEASH_CONTEXT_OPPRETTHOLDT_INNTEKT = "sykepengesoknad-backend-opprettholdt-inntekt"

@Component
class UnleashToggles(
    private val unleash: Unleash,
) {
    fun tilkommenInntektEnabled(fnr: String): Boolean =
        unleash.isEnabled(
            UNLEASH_CONTEXT_TILKOMMEN_INNTEKT,
            UnleashContext.builder().userId(fnr).build(),
        )

    fun sigrunPaaKafkaEnabled(fnr: String): Boolean =
        unleash.isEnabled(UNLEASH_CONTEXT_SIGRUN_PAA_KAFKA, UnleashContext.builder().userId(fnr).build())

    fun oppholdIUtlandetEnabled(fnr: String): Boolean =
        unleash.isEnabled(UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET, UnleashContext.builder().userId(fnr).build())

    fun opprettholdtInntektEnabled(fnr: String): Boolean =
        unleash.isEnabled(UNLEASH_CONTEXT_OPPRETTHOLDT_INNTEKT, UnleashContext.builder().userId(fnr).build())
}
