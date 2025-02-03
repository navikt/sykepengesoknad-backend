package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadsperiode
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
    private val friskTilArbeidRepository: FriskTilArbeidRepository?,
    private val sykepengesoknadDAO: SykepengesoknadDAO?,
    private val soknadProducer: SoknadProducer?,
) {
    private val log = logger()

    @Transactional
    fun opprettSoknad(friskTilArbeidDbRecord: FriskTilArbeidVedtakDbRecord) {
        // TODO: Lag en semantisk ID for hver søknad med bruk av fom og tom.
        val soknadId = UUID.randomUUID().toString()

        // TODO: Deserialiser det faktiske vedtaket og hent reelle verdier.
        val sykmeldingId = friskTilArbeidDbRecord.id
        val sykmeldingSkrevet = Instant.now()

        // TODO: Samme som hver søknads fom og tom.
        val soknadPerioder = listOf<Soknadsperiode>()

        // TODO: Trenger vi en egen Arbeidssituasjon?
        val arbeidssituasjon = Arbeidssituasjon.ANNET

        // TODO: expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
        val egenmeldingsDagerFraSykmelding = null

        // TODO: Sett ny avsendertype
        // TODO: Sett egenmeldt_sykmelding til false
        // TODO: Sett signaturdato

        // TODO: Del opp vedtaksperioden i flere søknader.
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
                forstegangssoknad = true,
                sykmeldingId = sykmeldingId,
                sykmeldingSkrevet = sykmeldingSkrevet,
                soknadPerioder = soknadPerioder,
                arbeidssituasjon = arbeidssituasjon,
                utenlandskSykmelding = false,
                egenmeldingsdagerFraSykmelding = egenmeldingsDagerFraSykmelding,
            )

        sykepengesoknadDAO!!.lagreSykepengesoknad(sykepengesoknad)
        friskTilArbeidRepository!!.save(friskTilArbeidDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
        soknadProducer!!.soknadEvent(sykepengesoknad)

        log.info(
            "Opprettet soknad med status: FREMTIDIG og " +
                "id: $soknadId fra FriskTilArbeidVedtakStatus med " +
                "id: ${friskTilArbeidDbRecord.id}.",
        )
    }
}
