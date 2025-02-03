package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FriskTilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Transactional
    fun opprettSoknad(dbRecord: FriskTilArbeidVedtakDbRecord) {
        friskTilArbeidRepository.save(dbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
        log.info("Behandlet FriskTilArbeidVedtakStatus med id: ${dbRecord.id}.")
    }
}
