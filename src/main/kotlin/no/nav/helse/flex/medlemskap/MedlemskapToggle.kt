package no.nav.helse.flex.medlemskap

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL = "sykepengesoknad-backend-soknad-for-sporsmal-om-medlemskap"

@Component
class MedlemskapToggle(
    private val unleash: Unleash
) {

    fun stillMedlemskapSporsmal(fnr: String): Boolean {
        val unleashContext = UnleashContext.builder().userId(fnr).build()
        return unleash.isEnabled(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL, unleashContext)
    }
}
