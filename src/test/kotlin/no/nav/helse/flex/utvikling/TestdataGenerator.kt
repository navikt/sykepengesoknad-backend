package no.nav.helse.flex.utvikling

import jakarta.annotation.PostConstruct
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev")
class TestdataGenerator {
    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    fun sendSm(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
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

        println("Sender sykmelding")
    }

    @PostConstruct
    fun generate() {
        println("Genererer testdata")
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = Companion.FNR,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                statusEvent = STATUS_SENDT,
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "Kebabbiten"),
            )
        val sykmelding =
            skapArbeidsgiverSykmelding()

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        sendSm(sykmeldingKafkaMessage)
    }

    companion object {
        const val FNR = "12345678910"
    }
}
