package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.repository.KlippMetrikkDbRecord
import no.nav.helse.flex.repository.KlippMetrikkRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class KlippMetrikk(private val klippMetrikkRepository: KlippMetrikkRepository) {

    fun klippMetrikk(klippMetrikkVariant: KlippMetrikkVariant, soknadstatus: String, sykmeldingId: String) {
        klippMetrikkRepository.save(
            KlippMetrikkDbRecord(
                id = null,
                sykmeldingUuid = sykmeldingId,
                variant = klippMetrikkVariant.name,
                soknadstatus = soknadstatus,
                timestamp = Instant.now()
            )
        )
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
