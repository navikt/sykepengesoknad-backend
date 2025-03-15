package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.BehandletStatus
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidCronJob
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StoppmeldingProsseseringTest : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111122"
    private val avsluttetTidspunkt = Instant.now()

    private var vedtaksId: String? = null

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        sendFriskTilArbeidVedtak(fnr, LocalDate.now().minusDays(15), LocalDate.now().plusDays(35))
    }

    @Test
    @Order(2)
    fun `Oppretter søknader med status NY og FREMTIDIG`() {
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

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
        vedtaksId = friskTilArbeidDbRecord.id
    }

    @Test
    @Order(3)
    fun `Send ArbeidssokerperiodeStoppMelding`() {
        val soknader = hentSoknader(fnr)
        val soknad = soknader.first { it.status == RSSoknadstatus.NY }
        soknader.shouldHaveSize(4)

        sendStoppMelding(soknad.friskTilArbeidVedtakId!!, fnr, avsluttetTidspunkt)
    }

    @Test
    @Order(4)
    fun `4 søknader er slettet og produsert på kafka`() {
        hentSoknader(fnr) shouldHaveSize 0

        SoknadKafkaProducerFake.records
            .filter { it.value().status == SoknadsstatusDTO.SLETTET }
            .filter { it.value().friskTilArbeidVedtakId == vedtaksId }.shouldHaveSize(4)
    }

    @Test
    @Order(5)
    fun `FriskTilArbeidVedtak er oppdatert med avsluttetTidspunkt`() {
        friskTilArbeidRepository.findByFnrIn(listOf(fnr))
            .single().avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
    }

    @Test
    @Order(6)
    fun `Tillatter nytt vedtak fom neste dag etter stopp tidspunktet`() {
        sendFriskTilArbeidVedtak(fnr, LocalDate.now().plusDays(1), LocalDate.now().plusDays(35))

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 1
    }

    @Disabled("Testen er flaky")
    @Test
    @Order(7)
    fun `Oppretter nye søknader med status FREMTIDIG`() {
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().maxByOrNull { it.opprettet }!!.also {
                it.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).sortedBy { it.fom }.also {
            it.size `should be equal to` 3
            it.map { it.status } `should be equal to`
                listOf(
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                )
        }
    }
}
