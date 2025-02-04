package no.nav.helse.flex.frisktilarbeid

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
        periodGenerator: (LocalDate, LocalDate, Long) -> List<Pair<LocalDate, LocalDate>>,
    ) {
        periodGenerator(vedtakDbRecord.fom, vedtakDbRecord.tom, SOKNAD_PERIODELENGDE).forEach { (fom, tom) ->
            lagSoknad(vedtakDbRecord, Soknadsperiode(fom, tom)).also { soknad ->
                sykepengesoknadDAO!!.lagreSykepengesoknad(soknad)
                soknadProducer!!.soknadEvent(soknad)
                log.info("Opprettet soknad med status: FREMTIDIG og id: ${soknad.id}")
            }
        }
        friskTilArbeidRepository.save(vedtakDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
    }

    private fun lagSoknad(
        vedtakDbRecord: FriskTilArbeidVedtakDbRecord,
        soknadsperiode: Soknadsperiode,
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

    private data class Soknadsperiode(val fom: LocalDate, val tom: LocalDate)
}

fun defaultSoknadPeriodeGenerator(
    fom: LocalDate,
    tom: LocalDate,
    periodeLengde: Long = SOKNAD_PERIODELENGDE,
): List<Pair<LocalDate, LocalDate>> {
    if (tom.isBefore(fom)) {
        throw IllegalArgumentException("Til-dato kan ikke være før fra-dato.")
    }
    return generateSequence(fom) {
        it.plusDays(periodeLengde).takeIf { next -> next.isBefore(tom) }
    }
        .map { fom -> fom to minOf(fom.plusDays(periodeLengde - 1), tom) }
        .toList()
}
