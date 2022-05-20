package no.nav.helse.flex.service

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.genererSykepengesoknadFraMetadata
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.helse.flex.logger
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.system.measureTimeMillis

@Service
@Transactional
class AktiverEnkeltSoknadService(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val identService: IdentService,
) {
    val log = logger()

    fun aktiverSoknad(id: String) {
        try {
            MDC.put(NAV_CALLID, UUID.randomUUID().toString())
            val aktiverTid = measureTimeMillis {
                sykepengesoknadDAO.aktiverSoknad(id)
            }
            val lagSpm = measureTimeMillis {

                lagSporsmalPaSoknad(id)
            }
            val publiserSoknad = measureTimeMillis {

                val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

                when (soknad.soknadstype) {
                    Soknadstype.OPPHOLD_UTLAND -> throw IllegalArgumentException("Søknad med type ${soknad.soknadstype.name} kan ikke aktiveres")
                    else -> soknadProducer.soknadEvent(soknad)
                }
            }
            log.info("Aktiverte søknad med id $id - Aktiver: $aktiverTid Spm: $lagSpm Kafka: $publiserSoknad")
        } catch (e: Exception) {
            throw e
        } finally {
            MDC.remove(NAV_CALLID)
        }
    }

    private fun lagSporsmalPaSoknad(id: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)
        val soknadMetadata = soknad.tilSoknadMetadata()
        val start = System.currentTimeMillis()

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
        val slutt = System.currentTimeMillis()
        log.info("Hentet identer for søknad med id $id - ${slutt - start}ms")

        val andreSoknader = sykepengesoknadDAO.finnSykepengesoknader(identer).filterNot { it.id == soknad.id }

        val generertSoknad = genererSykepengesoknadFraMetadata(soknadMetadata, andreSoknader)

        sykepengesoknadDAO.byttUtSporsmal(generertSoknad)
    }
}

fun Sykepengesoknad.tilSoknadMetadata(): SoknadMetadata {
    return SoknadMetadata(
        id = this.id,
        fnr = this.fnr,
        status = this.status,
        startSykeforlop = this.startSykeforlop!!,
        fom = this.fom!!,
        tom = this.tom!!,
        arbeidssituasjon = arbeidssituasjon!!,
        arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
        arbeidsgiverNavn = this.arbeidsgiverNavn,
        sykmeldingId = this.sykmeldingId!!,
        sykmeldingSkrevet = this.sykmeldingSkrevet!!,
        sykmeldingsperioder = this.soknadPerioder!!,
        egenmeldtSykmelding = this.egenmeldtSykmelding,
        merknader = this.merknaderFraSykmelding,
        soknadstype = this.soknadstype
    )
}
