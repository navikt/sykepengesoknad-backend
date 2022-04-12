package no.nav.syfo.migrering

import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.domain.ArbeidsgiverForskutterer
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Avsendertype
import no.nav.syfo.domain.Merknad
import no.nav.syfo.domain.Opprinnelse
import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.tilOsloInstant
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

const val INNSENDING_MIGRERING_TOPIC = "flex." + "syfosoknad-sykepengesoknad-migrering"

@Component
class SykepengesoknadImportListener(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    registry: MeterRegistry,
) {

    private val log = logger()
    val counter = registry.counter("importert_soknad_counter")

    @KafkaListener(
        topics = [INNSENDING_MIGRERING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "migrering-import-consumer",
        idIsGroup = true,
        batch = "true",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(records: List<ConsumerRecord<String, String>>, acknowledgment: Acknowledgment) {

        handterSoknader(records.map { it.value() })
        acknowledgment.acknowledge()
    }

    fun handterSoknader(records: List<String>) {

        val raderFraKafka = records
            .map { it.tilSykepengesoknadKafka() }
            .map { it.tilSykepengesoknad() }
            .map { it.fjernSvarFraUtgatt() }
            .map { it.fixGrenseverdier() }

        if (raderFraKafka.isEmpty()) {
            return
        }

        val elapsed = measureTimeMillis {
            raderFraKafka.forEach {
                if (sykepengesoknadDAO.eksistererSoknad(it.id)) {
                    log.info("Søknad ${it.id} eksisterte allerede i databasen")
                } else {
                    sykepengesoknadDAO.lagreSykepengesoknad(it)
                }
            }
            counter.increment(raderFraKafka.size.toDouble())
        }
        log.info("Behandlet ${raderFraKafka.size} soknader fra kafka i $elapsed millis")
    }
}

fun String.tilSykepengesoknadKafka(): SykepengesoknadKafka = OBJECT_MAPPER.readValue(this)

fun SykepengesoknadKafka.tilSykepengesoknad(): Sykepengesoknad {
    return Sykepengesoknad(
        id = id,
        fnr = fnr,
        soknadstype = soknadstype,
        status = status,
        opprettet = opprettet?.tilOsloInstant(),
        avbruttDato = avbruttDato,
        sendtNav = sendtNav?.tilOsloInstant(),
        korrigerer = korrigerer,
        korrigertAv = korrigertAv,
        sporsmal = sporsmal,
        opprinnelse = opprinnelse,
        avsendertype = avsendertype,
        sykmeldingId = sykmeldingId,
        fom = fom,
        tom = tom,
        startSykeforlop = startSykeforlop,
        sykmeldingSkrevet = sykmeldingSkrevet?.tilOsloInstant(),
        soknadPerioder = soknadPerioder,
        sendtArbeidsgiver = sendtArbeidsgiver?.tilOsloInstant(),
        arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
        arbeidsgiverNavn = arbeidsgiverNavn,
        arbeidssituasjon = arbeidssituasjon,
        egenmeldtSykmelding = egenmeldtSykmelding,
        merknaderFraSykmelding = merknaderFraSykmelding,
        avbruttFeilinfo = avbruttFeilinfo,
    )
}

data class SykepengesoknadKafka(
    val id: String,
    val fnr: String,
    val soknadstype: Soknadstype,
    val status: Soknadstatus,
    val opprettet: LocalDateTime?,
    val avbruttDato: LocalDate? = null,
    val sendtNav: LocalDateTime? = null,
    val korrigerer: String? = null,
    val korrigertAv: String? = null,
    val sporsmal: List<Sporsmal>,
    val opprinnelse: Opprinnelse = Opprinnelse.SYFOSOKNAD,
    val avsendertype: Avsendertype? = null,
    val sykmeldingId: String?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val startSykeforlop: LocalDate?,
    val sykmeldingSkrevet: LocalDateTime?,
    val soknadPerioder: List<Soknadsperiode>?,
    val sendtArbeidsgiver: LocalDateTime? = null,
    val arbeidsgiverForskutterer: ArbeidsgiverForskutterer? = null,
    val arbeidsgiverOrgnummer: String? = null,
    val arbeidsgiverNavn: String? = null,
    val arbeidssituasjon: Arbeidssituasjon?,
    val egenmeldtSykmelding: Boolean? = null,
    val merknaderFraSykmelding: List<Merknad>? = null,
    val avbruttFeilinfo: Boolean? = null,
)
