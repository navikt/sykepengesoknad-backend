package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.AVBRUTT
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GjenapneSykmeldingService(
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    fun prosesserTombstoneSykmelding(sykmeldingId: String) {
        val soknaderTilSykmeldingSomKanSlettes = sykepengesoknadDAO
            .finnSykepengesoknaderForSykmelding(sykmeldingId)
            .filter { listOf(NY, FREMTIDIG, AVBRUTT, UTKAST_TIL_KORRIGERING).contains(it.status) }

        if (soknaderTilSykmeldingSomKanSlettes.isEmpty()) {
            log.info("Mottok status åpen for sykmelding $sykmeldingId på kafka. Ingen tilhørende søknader.")
            return
        }

        if (soknaderTilSykmeldingSomKanSlettes.any { it.arbeidssituasjon == Arbeidssituasjon.ARBEIDSTAKER }) {
            log.error("Prosesserte åpen melding for $sykmeldingId,  men denne har arbeidstakersøknad. Den kan ikke endres så dette skal ikke skje. Da må være noe tidsforskyvelse")
            return
        }

        soknaderTilSykmeldingSomKanSlettes.slettSoknader()
    }

    private fun List<Sykepengesoknad>.slettSoknader() {
        this.map { it.copy(status = Soknadstatus.SLETTET) }
            .forEach {
                soknadProducer.soknadEvent(it, null, false)
                sykepengesoknadDAO.slettSoknad(it)
            }
    }
}
