package no.nav.syfo.overlappendesykmeldinger

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.syfo.domain.Arbeidsgiverperiode
import no.nav.syfo.domain.Periode
import no.nav.syfo.hentSoknader
import no.nav.syfo.mockArbeidsgiverForskutterer
import no.nav.syfo.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.syfo.service.AktiverService
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.ANSVARSERKLARING
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.syfo.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.syfo.soknadsopprettelse.FERIE_V2
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.syfo.soknadsopprettelse.JOBBET_DU_100_PROSENT
import no.nav.syfo.soknadsopprettelse.PERMISJON_V2
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE
import no.nav.syfo.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.syfo.soknadsopprettelse.UTDANNING
import no.nav.syfo.soknadsopprettelse.UTLAND_V2
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.tilSoknader
import no.nav.syfo.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OverlapperEtter : BaseTestClass() {

    @Autowired
    private lateinit var aktiverService: AktiverService

    private final val basisdato = LocalDate.now()

    @BeforeEach
    fun setUp() {
        mockArbeidsgiverForskutterer(Forskuttering.JA)
    }

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val fnr = "11111111111"
        sendArbeidstakerSykmelding(
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
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr,
            oppfolgingsdato = basisdato.minusDays(1),
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
            .ventPåRecords(antall = 2)
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
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr,
            oppfolgingsdato = basisdato.minusDays(1),
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
            .ventPåRecords(antall = 2)
            .tilSoknader()

        kafkaSoknader.all { it.status == SoknadsstatusDTO.NY } shouldBeEqualTo true
    }

    @Test
    @Order(5)
    fun `Databasen tømmes`() {
        databaseReset.resetDatabase()
    }

    @Test
    @Order(10)
    fun `Brukeren sender inn en sykmelding`() {
        val fnr = "55555555555"
        sendArbeidstakerSykmelding(
            fom = basisdato,
            tom = basisdato.plusDays(15),
            fnr = fnr
        )

        aktiverService.aktiverSoknader(basisdato.plusDays(16))

        val rsSoknad = hentSoknader(fnr)
            .shouldHaveSize(1)
            .first()

        mockFlexSyketilfelleArbeidsgiverperiode(
            arbeidsgiverperiode = Arbeidsgiverperiode(
                antallBrukteDager = 16,
                oppbruktArbeidsgiverperiode = true,
                arbeidsgiverPeriode = Periode(fom = rsSoknad.fom!!, tom = rsSoknad.tom!!)
            )
        )

        SoknadBesvarer(rsSoknad, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
            .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN, "NEI")
            .besvarSporsmal(PERMISJON_V2, "NEI")
            .besvarSporsmal(FERIE_V2, "NEI")
            .besvarSporsmal(UTLAND_V2, "NEI")
            .besvarSporsmal(PERMITTERT_NAA, "NEI")
            .besvarSporsmal(PERMITTERT_PERIODE, "NEI")
            .besvarSporsmal(JOBBET_DU_100_PROSENT + '0', "NEI")
            .besvarSporsmal(ARBEID_UTENFOR_NORGE, "NEI")
            .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
            .besvarSporsmal(UTDANNING, "NEI")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
            .sendSoknad()

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(11)
    fun `Overlappende sykmelding med samme grad blir klippet`() {
        val fnr = "55555555555"
        sendArbeidstakerSykmelding(
            fom = basisdato.plusDays(10),
            tom = basisdato.plusDays(20),
            fnr = fnr
        )

        val soknad = sykepengesoknadKafkaConsumer
            .ventPåRecords(antall = 1)
            .tilSoknader()
            .shouldHaveSize(1)
            .first()

        soknad.fom shouldBeEqualTo basisdato.plusDays(10) // Skal være .plusDays(15) når det klippes
        soknad.tom shouldBeEqualTo basisdato.plusDays(20)
    }
}
