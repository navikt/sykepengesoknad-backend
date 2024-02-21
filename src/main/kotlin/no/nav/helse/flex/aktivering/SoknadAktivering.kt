package no.nav.helse.flex.aktivering

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SoknadsperiodeDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SporsmalGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

@Service
@Transactional(rollbackFor = [Throwable::class])
class SoknadAktivering(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val soknadsperiodeDAO: SoknadsperiodeDAO,
    private val sporsmalGenerator: SporsmalGenerator,
    private val registry: MeterRegistry,
) {
    private final val log = logger()

    fun aktiverSoknad(id: String) {
        log.info("Forsøker å aktivere soknad $id.")

        val sok = sykepengesoknadRepository.findBySykepengesoknadUuid(id)

        if (sok == null) {
            log.warn("Søknad $id mangler fra databasen. Kan ha blitt klippet.")
            return
        }

        if (sok.status != Soknadstatus.FREMTIDIG) {
            log.warn("Søknad $id er allerede aktivert.")
            return
        }

        if (sok.soknadstype == Soknadstype.ARBEIDSTAKERE) {
            val perioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sok.id!!))[sok.id]!!
            val forventetFom = perioder.minOf { it.fom }
            val forventetTom = perioder.maxOf { it.tom }

            // TODO: Skriv noe mer om hvorfor det bare er greit å logge her.
            if (sok.fom != forventetFom || sok.tom != forventetTom) {
                log.warn(
                    "Søknad $id har perioder som starter på $forventetFom og slutter på $forventetTom, dette stemmer " +
                        "ikke med søknaden sin fom ${sok.fom} og tom ${sok.tom}. Aktiverer ikke søknaden.",
                )
                return
            }
        }

        val aktiverTid =
            measureTimeMillis {
                sykepengesoknadDAO.aktiverSoknad(id)
            }
        val lagSpm =
            measureTimeMillis {
                sporsmalGenerator.lagSporsmalPaSoknad(id)
            }
        val publiserSoknad =
            measureTimeMillis {
                val soknad = sykepengesoknadDAO.finnSykepengesoknad(id)

                when (soknad.soknadstype) {
                    Soknadstype.OPPHOLD_UTLAND -> throw IllegalArgumentException(
                        "Søknad med type ${soknad.soknadstype.name} kan ikke aktiveres",
                    )
                    else -> soknadProducer.soknadEvent(soknad)
                }
            }
        log.info(
            "Aktiverte søknad med id $id. Tid brukt på aktivering: $aktiverTid, spørsmålsgenerering: $lagSpm, publisering: $publiserSoknad",
        )
        registry.counter("aktiverte_sykepengesoknader").increment()
    }
}
