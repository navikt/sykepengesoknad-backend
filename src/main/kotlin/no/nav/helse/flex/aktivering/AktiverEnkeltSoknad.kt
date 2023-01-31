package no.nav.helse.flex.aktivering

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SporsmalGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

@Service
@Transactional
class AktiverEnkeltSoknad(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val sporsmalGenerator: SporsmalGenerator,
    private val registry: MeterRegistry,
) {
    val log = logger()

    fun aktiverSoknad(id: String) {

        log.info("Forsøker å aktivere soknad $id")

        val sok = sykepengesoknadRepository.findBySykepengesoknadUuid(id)

        if (sok == null) {
            log.warn("Søknad $id mangler fra databasen. Kan ha blitt klippet")
            return
        }

        if (sok.status != Soknadstatus.FREMTIDIG) {
            log.warn("Søknad $id er allerede aktivert")
            return
        }

        val aktiverTid = measureTimeMillis {
            sykepengesoknadDAO.aktiverSoknad(id)
        }
        val lagSpm = measureTimeMillis {
            sporsmalGenerator.lagSporsmalPaSoknad(id)
        }
        val publiserSoknad = measureTimeMillis {

            val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

            when (soknad.soknadstype) {
                Soknadstype.OPPHOLD_UTLAND -> throw IllegalArgumentException("Søknad med type ${soknad.soknadstype.name} kan ikke aktiveres")
                else -> soknadProducer.soknadEvent(soknad)
            }
        }
        log.info("Aktiverte søknad med id $id - Aktiver: $aktiverTid Spm: $lagSpm Kafka: $publiserSoknad")
        registry.counter("aktiverte_sykepengesoknader").increment()
    }
}
