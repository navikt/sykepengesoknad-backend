package no.nav.helse.flex.soknadsopprettelse.overlappendesykmeldinger

import no.nav.helse.flex.repository.KlippMetrikkDbRecord
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.KlippVariant
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class KlippMetrikk(private val klippMetrikkRepository: KlippMetrikkRepository) {

    fun klippMetrikk(
        klippMetrikkVariant: KlippVariant,
        soknadstatus: String,
        eksisterendeSykepengesoknadId: String,
        endringIUforegrad: Soknadsklipper.EndringIUforegrad,
        sykmeldingId: String,
        klippet: Boolean,
    ) {
        klippMetrikkRepository.save(
            KlippMetrikkDbRecord(
                id = null,
                sykmeldingUuid = sykmeldingId,
                eksisterendeSykepengesoknadId = eksisterendeSykepengesoknadId,
                endringIUforegrad = endringIUforegrad.name,
                klippet = klippet,
                variant = klippMetrikkVariant.name,
                soknadstatus = soknadstatus,
                timestamp = Instant.now()
            )
        )
    }
}
