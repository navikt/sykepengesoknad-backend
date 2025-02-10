package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.frisktilarbeid.lagFriskTilArbeidVedtakStatus
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.support.Acknowledgment
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationMedSporsmalTest() : FakesTestOppsett() {


    @Autowired
    lateinit var friskTilArbeidConsumer: FriskTilArbeidConsumer

    @Autowired
    lateinit var friskTilArbeidRepository: FriskTilArbeidRepository


    private val fnr = "11111111111"

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        // To 14-dagersperioder og én 7-dagersperiode.
        val vedtaksperiode =
            Periode(
                fom = LocalDate.of(2025, 2, 3),
                tom = LocalDate.of(2025, 3, 9),
            )
        val fnr = fnr
        val key = fnr.asProducerRecordKey()
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)

        friskTilArbeidConsumer.listen(
            ConsumerRecord(
                FRISKTILARBEID_TOPIC,
                0,
                0,
                key,
                friskTilArbeidVedtakStatus.serialisertTilString()
            )
        ) { }


        val vedtakSomSkalBehandles = friskTilArbeidRepository.finnVedtakSomSkalBehandles(10)

        vedtakSomSkalBehandles.size `should be equal to` 1
        vedtakSomSkalBehandles.first().also {
            it.fnr `should be equal to` fnr
            it.behandletStatus `should be equal to` BehandletStatus.NY
            it.fom `should be equal to` vedtaksperiode.fom
            it.tom `should be equal to` vedtaksperiode.tom
        }
    }

}
