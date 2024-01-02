package no.nav.helse.flex.sykmeldingmerknader

import no.nav.helse.flex.domain.Soknadstatus.*
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.tilMerknader
import no.nav.helse.flex.soknadsopprettelse.tilMerknader
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OppdateringAvMerknader(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    val log = logger()

    fun oppdaterMerknader(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        val soknader = sykepengesoknadRepository.findBySykmeldingUuid(sykmeldingKafkaMessage.sykmelding.id)

        soknader.forEach { soknad ->

            val soknadMerknader = soknad.merknaderFraSykmelding.tilMerknader()
            val sykmeldingMerknader = sykmeldingKafkaMessage.sykmelding.merknader.tilMerknader()
            if (soknadMerknader != sykmeldingMerknader) {
                val oppdatertMerknadForLog = sykmeldingMerknader?.joinToString(",") { it.type } ?: "null"
                log.info("Oppdaterer merknader for sykepengesoknad med id ${soknad.sykepengesoknadUuid} til $oppdatertMerknadForLog")

                val oppdatertSoknad = soknad.copy(merknaderFraSykmelding = sykmeldingMerknader?.serialisertTilString())
                sykepengesoknadRepository.save(oppdatertSoknad)
            }
        }
    }
}
