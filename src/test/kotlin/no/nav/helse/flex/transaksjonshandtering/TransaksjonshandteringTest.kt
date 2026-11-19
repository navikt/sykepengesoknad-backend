package no.nav.helse.flex.transaksjonshandtering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.outbox.OutboxDbRepository
import no.nav.helse.flex.outbox.OutboxType
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Duration

@TestMethodOrder(MethodOrderer.MethodName::class)
class TransaksjonshandteringTest : FellesTestOppsett() {
    final val fnr = "123456789"

    @Autowired
    lateinit var outboxDbRepository: OutboxDbRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = fnr,
            ),
        )
    }

    @Test
    fun `02 - vi besvarer alle sporsmal`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(FRISKMELDT, "JA")
            .oppsummering()
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "JA")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
            .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
    }

    @Test
    fun `04 - vi sender inn søknaden, kafka feiler, men meldingen ligger trygt i outboxen`() {
        doThrow(RuntimeException("sdfsdf")).whenever(aivenKafkaProducer).produserMelding(any())
        sendSoknadMedResult(fnr, hentSoknaderMetadata(fnr).first().id)
            .andExpect(MockMvcResultMatchers.status().isOk)

        val soknaden = hentSoknaderMetadata(fnr).first()
        assertThat(soknaden.status).isEqualTo(RSSoknadstatus.SENDT)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(aivenKafkaProducer, atLeastOnce()).produserMelding(any())
        }
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)

        val usendte = outboxDbRepository.findAll().filter { it.sendtTimestamp == null }
        usendte.map { it.type } shouldBe listOf(OutboxType.SYKEPENGESOKNAD)
        usendte.single().fnr shouldBe fnr
        (usendte.single().antallForsok > 0) shouldBe true
    }

    @Test
    fun `05 - kafka virker igjen og outboxen sender søknaden`() {
        // Hopper over ventetiden etter feilene i forrige test, så vi slipper å vente på backoff.
        jdbcTemplate.update("UPDATE outbox SET neste_forsok_timestamp = NULL WHERE sendt_timestamp IS NULL")

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.single().status shouldBe SoknadsstatusDTO.SENDT

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            outboxDbRepository.findAll().filter { it.sendtTimestamp == null } shouldBe emptyList()
        }
    }
}
