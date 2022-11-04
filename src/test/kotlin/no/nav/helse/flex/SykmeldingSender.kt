package no.nav.helse.flex

import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate

fun BaseTestClass.sendSykmelding(
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    oppfolgingsdato: LocalDate = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
    forventaSoknader: Int = 1,
): List<SykepengesoknadDTO> {
    flexSyketilfelleMockRestServiceServer?.reset()

    mockFlexSyketilfelleSykeforloep(
        sykmeldingKafkaMessage.sykmelding.id,
        oppfolgingsdato
    )

    val topic = if (sykmeldingKafkaMessage.event.statusEvent == STATUS_SENDT) {
        SYKMELDINGSENDT_TOPIC
    } else {
        SYKMELDINGBEKREFTET_TOPIC
    }

    kafkaProducer.send(
        ProducerRecord(
            topic,
            sykmeldingKafkaMessage.sykmelding.id,
            sykmeldingKafkaMessage.serialisertTilString()
        )
    )

    behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
        sykmeldingKafkaMessage.sykmelding.id,
        sykmeldingKafkaMessage
    )

    val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = forventaSoknader).tilSoknader()

    flexSyketilfelleMockRestServiceServer?.reset()
    return soknader
}

fun BaseTestClass.tombstoneSykmelding(
    sykmeldingId: String,
    topic: String = SYKMELDINGBEKREFTET_TOPIC,
) {
    kafkaProducer.send(ProducerRecord(topic, sykmeldingId, null)).get()
}
