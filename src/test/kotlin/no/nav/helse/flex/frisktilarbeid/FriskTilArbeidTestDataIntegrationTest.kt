package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.time.LocalDate

class FriskTilArbeidTestDataIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun klargjorListener() {
        databaseReset.resetDatabase()
        val listener = listenerRegistry.getListenerContainer("flex-frisktilarbeid-testdata-1").shouldNotBeNull()

        // Listeneren bruker latest. En fullført fetch sikrer at startposisjonen er satt før publisering.
        ventPaFriskTilArbeid("Testdata-listener har partisjoner og etablert startposisjon").until {
            !listener.assignedPartitions.isNullOrEmpty() &&
                listener.metrics().values.any { metrics ->
                    metrics.any { (navn, metrikk) ->
                        val verdi = metrikk.metricValue()
                        navn.name() == "fetch-total" && verdi is Number && verdi.toDouble() > 0
                    }
                }
        }
    }

    @Test
    fun `Mottar vedtak med status FATTET fra testdata-topic og lagrer det for behandling`() {
        val fnr = "11111111111"
        val key = fnr.asProducerRecordKey()
        val vedtak =
            lagFriskTilArbeidVedtakStatus(
                fnr,
                Status.FATTET,
                Periode(LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 23)),
            )

        kafkaProducer.send(ProducerRecord(FRISKTILARBEID_TESTDATA_TOPIC, key, vedtak.serialisertTilString())).get()

        forventTestdataVedtakLagretForBehandling(vedtak, key)
    }

    private fun forventTestdataVedtakLagretForBehandling(
        vedtak: FriskTilArbeidVedtakStatus,
        key: String,
    ) {
        ventPaFriskTilArbeid("Testdata-vedtak er lagret og klart for behandling").untilAsserted {
            val lagret = friskTilArbeidRepository.findByFnrIn(listOf(vedtak.personident)).shouldHaveSize(1).single()
            lagret.key shouldBe key
            lagret.fnr shouldBe vedtak.personident
            lagret.fom shouldBe vedtak.fom
            lagret.tom shouldBe vedtak.tom
            lagret.behandletStatus shouldBe BehandletStatus.NY
            lagret.behandletTidspunkt shouldBe null
            lagret.vedtak
                .shouldNotBeNull()
                .value
                .shouldNotBeNull()
                .tilFriskTilArbeidVedtakStatus() shouldBe vedtak
        }
    }
}
