package no.nav.helse.flex.unleash

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Component

const val UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL = "sykepengesoknad-backend-soknad-for-sporsmal-om-medlemskap"
const val UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER = "sykepengesoknad-backend-kjente-inntektskilder"
const val UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER =
    "sykepengesoknad-backend-naringsdrivende-inntektsopplysninger"

@Component
class UnleashToggles(
    private val unleash: Unleash,
) {
    fun stillMedlemskapSporsmal(fnr: String): Boolean {
        val unleashContext = UnleashContext.builder().userId(fnr).build()
        return unleash.isEnabled(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL, unleashContext)
    }

    fun stillKjenteInntektskilderSporsmal(fnr: String): Boolean {
        val unleashContext = UnleashContext.builder().userId(fnr).build()
        return unleash.isEnabled(UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER, unleashContext)
    }

    fun naringsdrivendeInntektsopplysninger(fnr: String): Boolean {
        return unleash.isEnabled(
            UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER,
            UnleashContext.builder().userId(fnr).build(),
        )
    }
}
