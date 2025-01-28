package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FrisktilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Transactional
    fun opprettSoknad(dbRecord: FriskTilArbeidDbRecord) {
        friskTilArbeidRepository.save(dbRecord.copy(status = BehandletStatus.BEHANDLET))
        log.info("Behandlet FriskTilArbeidVedtakStatus med id: ${dbRecord.id}.")
    }
}
