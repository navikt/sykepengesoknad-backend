package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.AVBRUTT
import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.UTKAST_TIL_KORRIGERING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GjenapneSykmeldingService(
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    fun prosesserTombstoneSykmelding(
        sykmeldingId: String,
        topic: String,
    ) {
        val soknaderTilSykmeldingSomKanSlettes =
            sykepengesoknadDAO
                .finnSykepengesoknaderForSykmelding(sykmeldingId)
                .filter { listOf(NY, FREMTIDIG, AVBRUTT, UTKAST_TIL_KORRIGERING).contains(it.status) }
                .filter { it.arbeidssituasjon != Arbeidssituasjon.ARBEIDSTAKER }

        if (soknaderTilSykmeldingSomKanSlettes.isEmpty()) {
            log.info("Mottok status åpen for sykmelding $sykmeldingId på kafka. Ingen tilhørende søknader.")
            return
        }

        if (topic == SYKMELDINGSENDT_TOPIC) {
            log.error("Prosesserte åpen melding for $sykmeldingId fra sendt topicet. Den kan ikke endres så dette skal ikke skje.")
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
