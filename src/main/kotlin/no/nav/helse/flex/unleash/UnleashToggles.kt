package no.nav.helse.flex.unleash

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_SAMMENLIGN_SYKMELDING_KAFKA = "sykepengesoknad-backend-sammenlign-sykmelding-kafka"
const val UNLEASH_CONTEXT_MELDING_TIL_NAVDAGER_KAFKA = "sykepengesoknad-backend-meldingtilnavdager-kafka"

@Component
class UnleashToggles(
    private val unleash: Unleash,
) {
    fun sammenlignSykmeldingKafkaEnabled(fnr: String): Boolean =
        unleash.isEnabled(
            UNLEASH_CONTEXT_SAMMENLIGN_SYKMELDING_KAFKA,
            UnleashContext.builder().userId(fnr).build(),
        )

    fun meldingTilNavDagerEnabled(fnr: String): Boolean =
        unleash.isEnabled(
            UNLEASH_CONTEXT_MELDING_TIL_NAVDAGER_KAFKA,
            UnleashContext.builder().userId(fnr).build(),
        )
}
