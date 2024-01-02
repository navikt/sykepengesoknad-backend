package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
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
        log.info(
            "Sletter tidligere ${this.status} søknad ${this.id} grunnet mottatt sykmelding ${this.sykmeldingId} som " +
                "tolkes til å være korrigert",
        )
        sykepengesoknadDAO.slettSoknad(slettet)
    }

    private fun Sykepengesoknad.settKorrigert() {
        log.info(
            "Setter tidligere ${this.status} søknad ${this.id} til korrigert grunnet mottatt sykmelding " +
                "${this.sykmeldingId} som tolkes til å være korrigert",
        )
        sykepengesoknadDAO.oppdaterStatus(this.copy(status = Soknadstatus.KORRIGERT))
    }
}
