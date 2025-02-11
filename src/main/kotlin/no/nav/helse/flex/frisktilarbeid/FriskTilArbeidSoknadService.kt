package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

const val SOKNAD_PERIODELENGDE = 14L

@Service
class FriskTilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val sykepengesoknadDAO: SykepengesoknadDAO?,
    private val soknadProducer: SoknadProducer?,
) {
    private val log = logger()

    @Transactional
    fun opprettSoknader(
        vedtakDbRecord: FriskTilArbeidVedtakDbRecord,
        periodeGenerator: (LocalDate, LocalDate, Long) -> List<Pair<LocalDate, LocalDate>> = ::defaultPeriodeGenerator,
    ): List<Sykepengesoknad> {
        val soknader =
            periodeGenerator(vedtakDbRecord.fom, vedtakDbRecord.tom, SOKNAD_PERIODELENGDE).map { (fom, tom) ->
                lagSoknad(vedtakDbRecord, Periode(fom, tom))
            }
        soknader.forEach {
            val lagretSoknad = sykepengesoknadDAO!!.lagreSykepengesoknad(it)
            soknadProducer!!.soknadEvent(lagretSoknad)
            log.info("Opprettet soknad: ${it.id} for FriskTilArbeidVedtakStatus med id: ${vedtakDbRecord.id}.")
        }

        friskTilArbeidRepository.save(vedtakDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
        return soknader
    }

    private fun lagSoknad(
        vedtakDbRecord: FriskTilArbeidVedtakDbRecord,
        soknadsperiode: Periode,
    ): Sykepengesoknad {
        val grunnlagForId =
            "${vedtakDbRecord.id}${soknadsperiode.fom}${soknadsperiode.tom}${vedtakDbRecord.opprettet}"
        val soknadId = UUID.nameUUIDFromBytes(grunnlagForId.toByteArray()).toString()

        val sykepengesoknad =
            Sykepengesoknad(
                id = soknadId,
                fnr = vedtakDbRecord.fnr,
                startSykeforlop = vedtakDbRecord.fom,
                fom = soknadsperiode.fom,
                tom = soknadsperiode.tom,
                soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                status = Soknadstatus.FREMTIDIG,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                friskTilArbeidVedtakId = vedtakDbRecord.id,
            )

        return sykepengesoknad
    }
}

fun defaultPeriodeGenerator(
    periodeStart: LocalDate,
    periodeSlutt: LocalDate,
    periodeLengde: Long = SOKNAD_PERIODELENGDE,
): List<Pair<LocalDate, LocalDate>> {
    if (periodeSlutt.isBefore(periodeStart)) {
        throw IllegalArgumentException("Til-dato kan ikke være før fra-dato.")
    }
    return generateSequence(periodeStart) {
        it.plusDays(periodeLengde).takeIf { it.isBefore(periodeSlutt) }
    }
        .map { fom -> fom to minOf(fom.plusDays(periodeLengde - 1), periodeSlutt) }
        .toList()
}
