package no.nav.helse.flex.frisktilarbeid.sporsmal

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
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
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationMedSporsmalTest : FakesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    lateinit var aktiveringJob: AktiveringJob

    private val fnr = "11111111132"

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        sendFriskTilArbeidVedtak(fnr, LocalDate.now().minusDays(15), LocalDate.now())
    }

    @Test
    @Order(2)
    fun `Oppretter søknader med status FREMTIDIG`() {
        friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size shouldBe 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus shouldBe BehandletStatus.BEHANDLET
            }

        await().until {
            sykepengesoknadRepository
                .findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!)
                .map { it.status }
                .sorted() ==
                listOf(
                    Soknadstatus.NY,
                    Soknadstatus.FREMTIDIG,
                )
        }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).sortedBy { it.fom }.also {
            it.size shouldBe 2
            it.map { it.status } shouldBe
                listOf(
                    Soknadstatus.NY,
                    Soknadstatus.FREMTIDIG,
                )
            it.forEach {
                it.friskTilArbeidVedtakId shouldBe friskTilArbeidDbRecord.id
            }
        }
    }

    @Test
    @Order(3)
    fun `Sortering av tags er riktig`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        soknad.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                FTA_JOBBSITUASJONEN_DIN,
                FTA_INNTEKT_UNDERVEIS,
                FTA_REISE_TIL_UTLANDET,
                TIL_SLUTT,
            )
        val jaSpørsmål = soknad.getSporsmalMedTag(FTA_JOBBSITUASJONEN_DIN_JA)
        jaSpørsmål.undersporsmal.map { it.tag }.shouldBe(
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
            }.sendSoknad()
    }

    @Test
    @Order(5)
    fun `Sendt søknad har riktig data`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.SENDT }
        val sendtSoknad = SoknadKafkaProducerFake.records.last().value()
        sendtSoknad.id shouldBe soknad.id
        sendtSoknad.inntektUnderveis!!.shouldBeFalse()
        sendtSoknad.fortsattArbeidssoker!!.shouldBeTrue()
    }

    @Test
    @Order(6)
    fun `Vi aktiverer den siste søknaden i perioden`() {
        aktiveringJob.bestillAktivering(LocalDate.now().plusDays(1))
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }
        val kafkaSoknad = SoknadKafkaProducerFake.records.last().value()
        kafkaSoknad.id shouldBe soknad.id
        kafkaSoknad.status shouldBe SoknadsstatusDTO.NY
    }

    @Test
    @Order(7)
    fun `Siste søknad har eget spørsmål`() {
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.NY }

        val jobbsitasjonenDin = soknad.getSporsmalMedTag(FTA_JOBBSITUASJONEN_DIN)
        val tags = listOf(jobbsitasjonenDin).flatten().map { it.tag }
        tags shouldBe
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
            }.sendSoknad()

        val kafkaSoknad = SoknadKafkaProducerFake.records.last().value()
        kafkaSoknad.id shouldBe soknad.id
        kafkaSoknad.status shouldBe SoknadsstatusDTO.SENDT
        kafkaSoknad.fortsattArbeidssoker shouldBe null
        kafkaSoknad.inntektUnderveis!!.shouldBeTrue()
    }
}
