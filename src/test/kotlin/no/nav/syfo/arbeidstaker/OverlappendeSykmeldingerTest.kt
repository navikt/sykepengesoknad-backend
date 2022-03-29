package no.nav.syfo.arbeidstaker

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.syfo.*
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.service.AktiverService
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.soknadsopprettelse.FERIE_V2
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlappendeSykmeldingerTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiverService: AktiverService

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    fun MockMvc.metrikker(): List<String> = this
        .perform(MockMvcRequestBuilders.get("/internal/prometheus"))
        .andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString.split("\n")

    final val basisdato = LocalDate.now()

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val fnr = "11111111111"
        opprettSykmelding(
            fom = basisdato.minusDays(1),
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 1

        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG

        val ventPåRecords = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(1))
        val kafkaSoknader = ventPåRecords.tilSoknader()

        kafkaSoknader[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Fremtidig arbeidstakersøknad opprettes for en overlappende sykmelding i scenario 1`() {
        val fnr = "11111111111"
        opprettSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.NY
        hentetViaRest[0].fom shouldBeEqualTo basisdato.minusDays(1)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = hentetViaRest[0].sporsmal?.find { it.tag == FRAVAR_FOR_SYKMELDINGEN }
        forsteSoknadSpmFinnes shouldNotBeEqualTo null
        val periodeSpmSok1 = hentetViaRest[0].sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok1?.min shouldBeEqualTo basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBeEqualTo basisdato.minusDays(1).toString()

        hentetViaRest[1].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[1].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        hentetViaRest[1].fom shouldBeEqualTo basisdato
        hentetViaRest[1].tom shouldBeEqualTo basisdato.plusDays(15)

        val kafkaSoknader = sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 2, duration = Duration.ofSeconds(1))
            .tilSoknader()

        kafkaSoknader[0].status shouldBeEqualTo SoknadsstatusDTO.NY
        kafkaSoknader[0].fom shouldBeEqualTo basisdato.minusDays(1)
        kafkaSoknader[0].tom shouldBeEqualTo basisdato.minusDays(1)

        kafkaSoknader[1].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader[1].fom shouldBeEqualTo basisdato
        kafkaSoknader[1].tom shouldBeEqualTo basisdato.plusDays(15)
    }

    @Test
    @Order(3)
    fun `Fremtidig arbeidstakersøknad klippes ikke når den er fullstendig overlappende`() {
        val fnr = "11111111111"
        opprettSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 3

        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.NY
        hentetViaRest[0].fom shouldBeEqualTo basisdato.minusDays(1)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.minusDays(1)

        hentetViaRest[1].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[1].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        hentetViaRest[1].fom shouldBeEqualTo basisdato
        hentetViaRest[1].tom shouldBeEqualTo basisdato.plusDays(15)

        hentetViaRest[2].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[2].status shouldBeEqualTo RSSoknadstatus.FREMTIDIG
        hentetViaRest[2].fom shouldBeEqualTo basisdato
        hentetViaRest[2].tom shouldBeEqualTo basisdato.plusDays(15)

        val kafkaSoknader = sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1, duration = Duration.ofSeconds(1))
            .tilSoknader()

        kafkaSoknader[0].status shouldBeEqualTo SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader[0].fom shouldBeEqualTo basisdato
        kafkaSoknader[0].tom shouldBeEqualTo basisdato.plusDays(15)
    }

    @Test
    @Order(4)
    fun `Søknadene aktiveres og får spørsmål tilpasset klippingen`() {
        val fnr = "11111111111"
        aktiverService.aktiverSoknader(basisdato.plusDays(16))

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 3

        hentetViaRest[0].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[0].status shouldBeEqualTo RSSoknadstatus.NY
        hentetViaRest[0].fom shouldBeEqualTo basisdato.minusDays(1)
        hentetViaRest[0].tom shouldBeEqualTo basisdato.minusDays(1)
        val forsteSoknadSpmFinnes = hentetViaRest[0].sporsmal?.find { it.tag == FRAVAR_FOR_SYKMELDINGEN }
        forsteSoknadSpmFinnes shouldNotBeEqualTo null
        val periodeSpmSok1 = hentetViaRest[0].sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok1?.min shouldBeEqualTo basisdato.minusDays(1).toString()
        periodeSpmSok1?.max shouldBeEqualTo basisdato.minusDays(1).toString()

        hentetViaRest[1].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[1].status shouldBeEqualTo RSSoknadstatus.NY
        hentetViaRest[1].fom shouldBeEqualTo basisdato
        hentetViaRest[1].tom shouldBeEqualTo basisdato.plusDays(15)
        val finnesIkke = hentetViaRest[1].sporsmal?.find { it.tag == FRAVAR_FOR_SYKMELDINGEN }
        finnesIkke shouldBeEqualTo null
        val periodeSpmSok2 = hentetViaRest[1].sporsmal
            ?.find { it.tag == FERIE_V2 }
            ?.undersporsmal
            ?.first()
        periodeSpmSok2?.min shouldBeEqualTo basisdato.toString()
        periodeSpmSok2?.max shouldBeEqualTo basisdato.plusDays(15).toString()

        hentetViaRest[2].soknadstype shouldBeEqualTo RSSoknadstype.ARBEIDSTAKERE
        hentetViaRest[2].status shouldBeEqualTo RSSoknadstatus.NY
        hentetViaRest[2].fom shouldBeEqualTo basisdato
        hentetViaRest[2].tom shouldBeEqualTo basisdato.plusDays(15)

        val kafkaSoknader = sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 2, duration = Duration.ofSeconds(1))
            .tilSoknader()

        kafkaSoknader.all { it.status == SoknadsstatusDTO.NY } shouldBeEqualTo true
    }

    @Test
    @Order(5)
    fun `Overlapper fullstendig`() {
        val fnr = "22222222222"
        opprettSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        opprettSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(1))
    }

    @Test
    @Order(6)
    fun `Overlapper før`() {
        val fnr = "33333333333"
        opprettSykmelding(
            fom = basisdato.plusDays(5),
            tom = basisdato.plusDays(10),
            fnr = fnr
        )
        opprettSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(7),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(1))
    }

    @Test
    @Order(7)
    fun `Overlapper inni`() {
        val fnr = "44444444444"
        opprettSykmelding(
            fom = LocalDate.of(2025, 1, 6),
            tom = LocalDate.of(2025, 1, 19),
            fnr = fnr
        )
        opprettSykmelding(
            fom = LocalDate.of(2025, 1, 9),
            tom = LocalDate.of(2025, 1, 12),
            fnr = fnr
        )

        val hentetViaRest = hentSoknader(fnr)
        hentetViaRest shouldHaveSize 2

        mockMvc.metrikker()
            .filter { it.contains("overlapper_inni_perioder_total") }
            .first { !it.startsWith("#") }
            .shouldBeEqualTo("""overlapper_inni_perioder_total{perioder="3-4-7",type="info",} 1.0""")

        mockMvc.metrikker()
            .filter { it.contains("overlapper_inni_perioder_uten_helg_total") }
            .first { !it.startsWith("#") }
            .shouldBeEqualTo("""overlapper_inni_perioder_uten_helg_total{perioder="3-2-5",type="info",} 1.0""")

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 2, duration = Duration.ofSeconds(1))
    }

    private fun opprettSykmelding(
        fom: LocalDate,
        tom: LocalDate,
        fnr: String,
    ) {
        flexSyketilfelleMockRestServiceServer?.reset()
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Butikken")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = fom,
            tom = tom,
        )

        mockFlexSyketilfelleSykeforloep(
            sykmelding.id,
            basisdato.minusDays(1)
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )
        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)
    }
}
