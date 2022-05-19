package no.nav.syfo.service

import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.kafka.NAV_CALLID
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.genererSykepengesoknadFraMetadata
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
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
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
