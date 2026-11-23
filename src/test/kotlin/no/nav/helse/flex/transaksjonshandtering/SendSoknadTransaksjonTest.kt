package no.nav.helse.flex.transaksjonshandtering

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.outbox.OutboxDbRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSoknadMedResult
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

@TestMethodOrder(MethodOrderer.MethodName::class)
class SendSoknadTransaksjonTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var outboxDbRepository: OutboxDbRepository

    private val fnr = "12345678900"
    private val fom = LocalDate.of(2024, 9, 6)
    private val tom = LocalDate.of(2024, 9, 25)

    @Test
    fun `01 - vi oppretter og besvarer en arbeidstakersøknad med opphold utenfor EØS`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(fom, tom),
                timestamp = fom.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
            ),
        )

        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId =
                    hentSoknaderMetadata(fnr)
                        .first { it.soknadstype == RSSoknadstype.ARBEIDSTAKERE && it.status == RSSoknadstatus.NY }
                        .id,
                fnr = fnr,
            )

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS_NAR", svar = """{"fom":"$fom","tom":"$tom"}""")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .oppsummering()
    }

    @Test
    fun `02 - opprettelse av utlandssøknad feiler, og hele innsendingen rulles tilbake`() {
        val soknadId = arbeidstakersoknadId()
        val antallOutboxRaderFor = outboxDbRepository.count()

        doThrow(RuntimeException("Feil ved opprettelse av utlandssøknad"))
            .whenever(soknadProducer)
            .soknadEvent(
                argWhere { it.soknadstype == Soknadstype.OPPHOLD_UTLAND },
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSoknadMedResult(fnr, soknadId).andExpect(status().isInternalServerError)

        sykepengesoknadDAO.finnSykepengesoknad(soknadId).status shouldBe Soknadstatus.NY
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr), Soknadstype.OPPHOLD_UTLAND).size shouldBe 0
        outboxDbRepository.count() shouldBe antallOutboxRaderFor
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 0)
    }

    @Test
    fun `03 - ny innsending går gjennom når feilen er borte`() {
        val soknadId = arbeidstakersoknadId()

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        sendSoknadMedResult(fnr, soknadId).andExpect(status().isOk)

        sykepengesoknadDAO.finnSykepengesoknad(soknadId).status shouldBe Soknadstatus.SENDT
        sykepengesoknadDAO.finnSykepengesoknader(listOf(fnr), Soknadstype.OPPHOLD_UTLAND).size shouldBe 1

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2).tilSoknader()
        kafkaSoknader.map { it.type to it.status } shouldBe
            listOf(
                SoknadstypeDTO.ARBEIDSTAKERE to SoknadsstatusDTO.SENDT,
                SoknadstypeDTO.OPPHOLD_UTLAND to SoknadsstatusDTO.NY,
            )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 3)
    }

    private fun arbeidstakersoknadId() =
        hentSoknaderMetadata(fnr)
            .first { it.soknadstype == RSSoknadstype.ARBEIDSTAKERE }
            .id
}
