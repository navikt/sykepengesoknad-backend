@file:Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")

package no.nav.helse.flex.aktivering

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
) {
    private final val log = logger()

    fun aktiverSoknad(id: String) {
        log.info("Forsøker å aktivere soknad $id.")

        val soknad = sykepengesoknadRepository.findBySykepengesoknadUuid(id)

        if (soknad == null) {
            log.warn("Søknad $id mangler fra databasen. Kan ha blitt klippet.")
            return
        }

        if (soknad.status != Soknadstatus.FREMTIDIG) {
            log.warn("Søknad $id er allerede aktivert.")
            return
        }

        if (soknad.soknadstype == Soknadstype.ARBEIDSTAKERE) {
            val perioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(soknad.id!!))[soknad.id]!!
            val forventetFom = perioder.minOf { it.fom }
            val forventetTom = perioder.maxOf { it.tom }

            if (soknad.fom != forventetFom || soknad.tom != forventetTom) {
                throw IllegalStateException(
                    "Søknad $id har perioder som starter: $forventetFom og slutter: $forventetTom. Det stemmer " +
                        "ikke med søknadens fom: ${soknad.fom} og tom: ${soknad.tom}.",
                )
            }
        }

        val aktiverTid =
            measureTimeMillis {
                sykepengesoknadDAO.aktiverSoknad(id)
            }
        val lagSporsmalTid =
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
            "Aktiverte søknad med id $id. Tid brukt på aktivering: $aktiverTid, " +
                "spørsmålsgenerering: $lagSporsmalTid, publisering: $publiserSoknad",
        )
    }
}
