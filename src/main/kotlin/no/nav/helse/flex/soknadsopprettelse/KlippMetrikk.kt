package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.repository.KlippMetrikkRepository
import org.springframework.stereotype.Component

@Component
class KlippMetrikk(private val klippMetrikkRepository: KlippMetrikkRepository) {

    fun klippMetrikk(klippMetrikkVariant: KlippMetrikkVariant, soknadstatus: String, sykmeldingId: String) {
    }

    enum class KlippMetrikkVariant {
        INNI,
        FØR,
        ETTER,
        SCENARIO_1_MOTSATT,
        SCENARIO_3_MOTSATT,
        FULLSTENDIG,
    }
}
