package no.nav.helse.flex.frisktilarbeid.sporsmal

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationMedSporsmalTest() : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var aktiveringJob: AktiveringJob

    private val fnr = "11111111111"

    @BeforeAll
    override fun slettDatabase() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
    }

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        sendFriskTilArbeidVedtak(fnr, LocalDate.now().minusDays(15), LocalDate.now())
    }

    @Test
    @Order(2)
    fun `Oppretter søknader med status FREMTIDIG`() {
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).sortedBy { it.fom }.also {
            it.size `should be equal to` 2
            it.map { it.status } `should be equal to`
                listOf(
                    Soknadstatus.NY,
                    Soknadstatus.FREMTIDIG,
                )
            it.forEach {
                it.friskTilArbeidVedtakId `should be equal to` friskTilArbeidDbRecord.id
            }
        }
    }

    @Test
    @Order(3)
    fun `Sortering av tags er riktig`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )
        val jaSpørsmål = soknad.sporsmal!!.flatten().first { it.tag == FTA_JOBBSITUASJONEN_DIN_JA }
        jaSpørsmål.undersporsmal.map { it.tag }.`should be equal to`(
            listOf(
                FTA_JOBBSITUASJONEN_DIN_NAR,
                FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB,
            ),
        )
    }

    @Test
    @Order(4)
    fun `Besvar alt`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", false)
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "JA", true)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "NEI")
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
            .sendSoknad()
    }

    @Test
    @Order(5)
    fun `Sendt søknad har riktig data`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.SENDT }
        val sendtSoknad = SoknadKafkaProducerFake.records.last().value()
        sendtSoknad.id `should be equal to` soknad.id
        sendtSoknad.inntektUnderveis!!.`should be false`()
        sendtSoknad.fortsattArbeidssoker!!.`should be true`()
    }

    @Test
    @Order(6)
    fun `Vi aktiverer den siste søknaden i perioden`() {
        aktiveringJob.bestillAktivering(LocalDate.now().plusDays(1))
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        val kafkaSoknad = SoknadKafkaProducerFake.records.last().value()
        kafkaSoknad.id `should be equal to` soknad.id
        kafkaSoknad.status `should be equal to` SoknadsstatusDTO.NY
    }

    @Test
    @Order(7)
    fun `Siste søknad har eget spørsmål`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }

        val jobbsitasjonenDin = soknad.sporsmal!!.find { it.tag == FTA_JOBBSITUASJONEN_DIN }!!
        val tags = listOf(jobbsitasjonenDin).flatten().map { it.tag }
        tags `should be equal to`
            listOf(
                FTA_JOBBSITUASJONEN_DIN,
                FTA_JOBBSITUASJONEN_DIN_JA,
                FTA_JOBBSITUASJONEN_DIN_NAR,
                FTA_JOBBSITUASJONEN_DIN_NEI,
            )
    }

    @Test
    @Order(8)
    fun `Besvar siste søknaden`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED", true)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS, "JA", ferdigBesvart = false)
            .besvarSporsmal(FTA_INNTEKT_UNDERVEIS_BELOP, "4000", ferdigBesvart = true)
            .besvarSporsmal(FTA_REISE_TIL_UTLANDET, "NEI")
            .oppsummering()
            .also {
                assertThat(it.muterteSoknaden).isFalse()
            }
            .sendSoknad()

        val kafkaSoknad = SoknadKafkaProducerFake.records.last().value()
        kafkaSoknad.id `should be equal to` soknad.id
        kafkaSoknad.status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknad.fortsattArbeidssoker `should be equal to` null
        kafkaSoknad.inntektUnderveis!!.`should be true`()
    }
}
