package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.sporsmal.sendFriskTilArbeidVedtak
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FremtidigeSoknaderFjernesVedIkkeLengreFriskmeldtTest : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    private val fnr = "11111111122"

    private var vedtaksId: String? = null

    val vedtakFom = LocalDate.now().minusDays(15)
    val vedtakTom = LocalDate.now().plusDays(35)

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        sendFriskTilArbeidVedtak(fnr, vedtakFom, vedtakTom)
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

        await().until {
            sykepengesoknadRepository
                .findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!)
                .map { it.status }
                .sorted() ==
                listOf(
                    Soknadstatus.NY,
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                    Soknadstatus.FREMTIDIG,
                )
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
    fun `Besvarer første søknad med vil ikke være arbeidssøker`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI", false)
            .besvarSporsmal(
                FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR,
                soknad.fom!!.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                true,
                mutert = true,
            ).besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .sendSoknad()
    }

    @Test
    @Order(5)
    fun `3 søknader er slettet og produsert på kafka`() {
        SoknadKafkaProducerFake.records
            .filter { it.value().status == SoknadsstatusDTO.SLETTET }
            .filter { it.value().friskTilArbeidVedtakId == vedtaksId }
            .shouldHaveSize(3)
    }

    @Test
    @Order(6)
    fun `resterende 1 søknader er den første sendte`() {
        val soknader = hentSoknader(fnr).sortedBy { it.fom } shouldHaveSize 1
        soknader[0].status `should be equal to` RSSoknadstatus.SENDT
        soknader[0].fom!! `should be equal to` vedtakFom
    }

    @Test
    @Order(7)
    fun `FriskTilArbeidVedtak er oppdatert med avsluttetTidspunkt`() {
        val avsluttetTidspunkt =
            friskTilArbeidRepository
                .findByFnrIn(listOf(fnr))
                .single()
                .avsluttetTidspunkt
                .shouldNotBeNull()

        avsluttetTidspunkt `should be within seconds of` Pair(1, Instant.now())
    }
}
