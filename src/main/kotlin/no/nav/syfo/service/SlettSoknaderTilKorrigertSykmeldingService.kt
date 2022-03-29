package no.nav.syfo.service

import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service

@Service
class SlettSoknaderTilKorrigertSykmeldingService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
) {
    val log = logger()

    fun slettSoknader(soknader: List<Sykepengesoknad>) {
        soknader.forEach {
            when (it.status) {
                Soknadstatus.NY -> it.slettSoknad(publiser = true)
                Soknadstatus.SENDT -> it.settKorrigert()
                Soknadstatus.FREMTIDIG -> it.slettSoknad(publiser = true)
                Soknadstatus.UTKAST_TIL_KORRIGERING -> it.slettSoknad(publiser = false)
                Soknadstatus.KORRIGERT -> Unit
                Soknadstatus.AVBRUTT -> it.slettSoknad(publiser = false)
                Soknadstatus.UTGATT -> it.slettSoknad(publiser = false)
                Soknadstatus.SLETTET -> it.slettSoknad(publiser = false)
            }
        }
    }

    private fun Sykepengesoknad.slettSoknad(publiser: Boolean) {
        val slettet = this.copy(status = Soknadstatus.SLETTET)

        if (publiser) {
            soknadProducer.soknadEvent(slettet, null, false)
        }
        log.info("Sletter tidligere ${this.status} søknad ${this.id} grunnet mottatt sykmelding ${this.sykmeldingId} som tolkes til å være korrigert")
        sykepengesoknadDAO.slettSoknad(slettet)
    }

    private fun Sykepengesoknad.settKorrigert() {
        log.info("Setter tidligere ${this.status} søknad ${this.id} til korrigert grunnet mottatt sykmelding ${this.sykmeldingId} som tolkes til å være korrigert")
        sykepengesoknadDAO.oppdaterStatus(this.copy(status = Soknadstatus.KORRIGERT))
    }
}
