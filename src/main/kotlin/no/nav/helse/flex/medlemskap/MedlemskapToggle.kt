package no.nav.helse.flex.medlemskap

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MedlemskapToggle(
    // Styret av om det er DEV eller PROD. I PROD stilles det ikke medlemskapspørsmål enda.
    @Value("\${ENABLE_MEDLEMSKAP}") private var stillMedlemskapSporsmal: Boolean
) {

    fun stillMedlemskapSporsmal(fnr: String): Boolean {
        // TODO: Implementer throttling med Unleash basert på fnr.
        return stillMedlemskapSporsmal
    }
}
