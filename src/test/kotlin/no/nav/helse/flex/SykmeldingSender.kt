package no.nav.helse.flex

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Venteperiode
import no.nav.helse.flex.domain.VenteperiodeResponse
import no.nav.helse.flex.domain.mapper.tilSoknadstatusDTO
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.consumer.SYKMELDINGBEKREFTET_TOPIC
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.soknadsopprettelse.hentArbeidssituasjon
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import java.time.LocalDate

fun FellesTestOppsett.sendSykmelding(
    sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    oppfolgingsdato: LocalDate = sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.minOf { it.fom },
    forventaSoknader: Int = 1,
): List<SykepengesoknadDTO> {
    flexSyketilfelleMockRestServiceServer.reset()

    if (sykmeldingKafkaMessage.hentArbeidssituasjon() in
        listOf(
            Arbeidssituasjon.FRILANSER,
            Arbeidssituasjon.NAERINGSDRIVENDE,
        )
    ) {
        mockFlexSyketilfelleErUtenforVentetid(sykmeldingKafkaMessage.sykmelding.id, true)
        mockFlexSyketilfelleVenteperiode(
            sykmeldingKafkaMessage.sykmelding.id,
            VenteperiodeResponse(Venteperiode(fom = LocalDate.now(), tom = LocalDate.now().plusDays(16))),
        )
    }

    mockFlexSyketilfelleSykeforloep(
        sykmeldingKafkaMessage.sykmelding.id,
        oppfolgingsdato,
    )

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

    val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = forventaSoknader).tilSoknader()

    soknader.forEach {
        await().until {
            if (it.status == SoknadsstatusDTO.SLETTET) {
                return@until true
            }
            sykepengesoknadRepository.findBySykepengesoknadUuid(it.id)?.status?.tilSoknadstatusDTO() == it.status
        }
    }

    flexSyketilfelleMockRestServiceServer.reset()
    return soknader
}
