package no.nav.helse.flex.medlemskap

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MedlemskapToggle(
    @Value("\${ENABLE_MEDLEMSKAP}")
    private var stillMedlemskapSporsmal: Boolean
) {

    fun stillMedlemskapSporsmal(fnr: String): Boolean {
        // TODO: Implementer throttling med Unleash basert på fnr.
        return stillMedlemskapSporsmal
    }
}
