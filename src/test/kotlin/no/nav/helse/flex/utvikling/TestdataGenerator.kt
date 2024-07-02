package no.nav.helse.flex.utvikling

import io.getunleash.FakeUnleash
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.util.serialisertTilString
import no.nav.security.token.support.core.api.Unprotected
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.OffsetDateTime

@RestController
@RequestMapping(value = ["/api/v2"])
@Profile("dev")
class TestdataGenerator {
    data class SykmeldingApiResponse(
        val id: String,
        val mottattTidspunkt: OffsetDateTime,
        val syketilfelleStartDato: LocalDate?,
        val behandletTidspunkt: OffsetDateTime,
        val arbeidsgiver: ArbeidsgiverAGDTO,
        val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
        val sykmeldingStatus: SykmeldingStatusKafkaEventDTO,
    )

    @GetMapping(value = ["/sykmeldinger"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @Unprotected
    fun sykmeldingerGetApi(): List<SykmeldingApiResponse> {
        return sykmeldinger.map {
            SykmeldingApiResponse(
                id = it.sykmelding.id,
                mottattTidspunkt = it.sykmelding.mottattTidspunkt,
                syketilfelleStartDato = it.sykmelding.syketilfelleStartDato,
                behandletTidspunkt = it.sykmelding.behandletTidspunkt,
                arbeidsgiver = it.sykmelding.arbeidsgiver,
                sykmeldingsperioder = it.sykmelding.sykmeldingsperioder,
                sykmeldingStatus = it.event,
            )
        }
    }

    val sykmeldinger = mutableListOf<SykmeldingKafkaMessage>()

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var fakeUnleash: FakeUnleash

    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        sykmeldinger.add(sykmeldingKafkaMessage)
        val topic =
            if (sykmeldingKafkaMessage.event.statusEvent == STATUS_SENDT) {
                SYKMELDINGSENDT_TOPIC
            } else {
                SYKMELDINGBEKREFTET_TOPIC
            }

        kafkaProducer.send(
            ProducerRecord(
                topic,
                sykmeldingKafkaMessage.sykmelding.id,
                sykmeldingKafkaMessage.serialisertTilString(),
            ),
        )
    }

    @PostConstruct
    fun toggleSporsmal() {
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL)
        fakeUnleash.enable(UNLEASH_CONTEXT_NY_OPPHOLD_UTENFOR_EOS)
    }

    @PostConstruct
    fun genererTestdata() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = FNR,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                statusEvent = STATUS_SENDT,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "987654321", orgNavn = "Gatekjøkkenet"),
            )
        val sykmelding =
            skapArbeidsgiverSykmelding(
                fom = LocalDate.now().minusDays(17),
                tom = LocalDate.now().minusDays(1),
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        sendSykmelding(sykmeldingKafkaMessage)
    }

    companion object {
        const val FNR = "12345678910"
    }
}
