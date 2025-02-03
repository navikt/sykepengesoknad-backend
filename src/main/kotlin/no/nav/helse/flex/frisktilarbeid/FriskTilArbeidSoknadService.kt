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
import java.util.*

@Service
class FriskTilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val sykepengesoknadDAO: SykepengesoknadDAO?,
    private val soknadProducer: SoknadProducer?,
) {
    private val log = logger()

    @Transactional
    fun opprettSoknad(friskTilArbeidDbRecord: FriskTilArbeidVedtakDbRecord) {
        val fom = friskTilArbeidDbRecord.fom
        val tom = friskTilArbeidDbRecord.fom.plusWeeks(2L)
        val seed = "${friskTilArbeidDbRecord.id}$fom$tom${friskTilArbeidDbRecord.opprettet}"
        val soknadId = UUID.nameUUIDFromBytes(seed.toByteArray()).toString()

        val sykepengesoknad =
            Sykepengesoknad(
                id = soknadId,
                fnr = friskTilArbeidDbRecord.fnr,
                startSykeforlop = friskTilArbeidDbRecord.fom,
                fom = friskTilArbeidDbRecord.fom,
                tom = friskTilArbeidDbRecord.fom.plusWeeks(2L),
                soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                status = Soknadstatus.FREMTIDIG,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                friskTilArbeidVedtakId = friskTilArbeidDbRecord.id,
            )

        sykepengesoknadDAO!!.lagreSykepengesoknad(sykepengesoknad)
        friskTilArbeidRepository.save(friskTilArbeidDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
        soknadProducer!!.soknadEvent(sykepengesoknad)

        log.info(
            "Opprettet soknad med status: FREMTIDIG og " +
                "id: $soknadId fra FriskTilArbeidVedtakStatus med " +
                "id: ${friskTilArbeidDbRecord.id}.",
        )
    }
}
