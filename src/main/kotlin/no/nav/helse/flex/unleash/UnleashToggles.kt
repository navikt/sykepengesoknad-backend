package no.nav.helse.flex.unleash

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_TILKOMMEN_INNTEKT =
    "sykepengesoknad-backend-tilkommen-inntekt"
const val UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS = "sykepengesoknad-backend-ny-opphold-utenfor-eos"

@Component
class UnleashToggles(
    private val unleash: Unleash,
) {
    fun tilkommenInntektEnabled(fnr: String): Boolean {
        return unleash.isEnabled(
            UNLEASH_CONTEXT_TILKOMMEN_INNTEKT,
            UnleashContext.builder().userId(fnr).build(),
        )
    }

    fun stillNySporsmalOmOppholdUtenforEOS(fnr: String): Boolean {
        val unleashContext = UnleashContext.builder().userId(fnr).build()
        return unleash.isEnabled(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS, unleashContext)
    }
}
