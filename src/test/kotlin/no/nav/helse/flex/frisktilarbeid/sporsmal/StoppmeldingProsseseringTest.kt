package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StoppmeldingProsseseringTest : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111111"

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        sendFtaVedtak(fnr, LocalDate.now().minusDays(15), LocalDate.now().plusDays(35))
    }

    @Test
    @Order(2)
    fun `Oppretter søknader med status FREMTIDIG`() {
        friskTilArbeidCronJob.startBehandlingAvFriskTilArbeidVedtakStatus()

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).sortedBy { it.fom }.also {
            it.size `should be equal to` 4
            it.map { it.status } `should be equal to`
                listOf(
                    Soknadstatus.NY,
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                )
        }
    }

    @Test
    @Order(3)
    fun `Vi sender en stopp melding`() {
        val soknader = hentSoknader(fnr)
        val soknad = soknader.first { it.status == RSSoknadstatus.NY }
        soknader.shouldHaveSize(4)
        sendStoppMelding(soknad.friskTilArbeidVedtakId!!, fnr)
    }

    @Test
    @Order(4)
    fun `3 søknader er slettet og produsert på kafka`() {
        val soknader = hentSoknader(fnr) shouldHaveSize 1

        SoknadKafkaProducerFake.records
            .filter { it.value().status == SoknadsstatusDTO.SLETTET }
            .filter { it.value().friskTilArbeidVedtakId == soknader.first().friskTilArbeidVedtakId }.shouldHaveSize(3)
    }
}
