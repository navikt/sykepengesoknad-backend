package no.nav.syfo.reisetilskudd

import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.syfo.*
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.controller.domain.sykepengesoknad.RSSvar
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Kvittering
import no.nav.syfo.domain.Utgiftstype
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import no.nav.syfo.soknadsopprettelse.sporsmal.vaerKlarOverAtReisetilskudd
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.testutil.byttSvar
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ReisetilskuddIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService

    final val fnr = "123456789"
    final val aktorid = fnr + "00"

    companion object {
        val sykmeldingId = UUID.randomUUID().toString()
        val tom = LocalDate.now().minusDays(1)
        val fom = LocalDate.now().minusDays(5)
    }

    @BeforeEach
    fun setUp() {
        whenever(bucketUploaderClient.slettKvittering(any())).thenReturn(true)
    }

    @Test
    @Order(0)
    fun `Det er ingen søknader til å begynne med`() {
        val soknader = this.hentSoknader(fnr)
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `01 - vi oppretter en reisetilskuddsøknad`() {

        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(
            fnr = fnr,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            statusEvent = STATUS_SENDT,
            arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "Kebabbiten")

        )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = getSykmeldingDto(
            sykmeldingId = sykmeldingId,
            fom = fom,
            tom = tom,
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.REISETILSKUDD)
    }

    @Test
    @Order(2)
    fun `02 - søknaden har alle spørsmål`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)
        val soknaden = soknader.first()

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                UTBETALING,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )

        assertThat(soknaden.sporsmal!!.first { it.tag == ANSVARSERKLARING }.sporsmalstekst).isEqualTo("Jeg vet at jeg kan miste retten til reisetilskudd og sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart.")
        assertThat(soknaden.sporsmal!!.first { it.tag == VAER_KLAR_OVER_AT }.sporsmalstekst).isEqualTo(
            vaerKlarOverAtReisetilskudd().sporsmalstekst
        )
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        this.avbrytSoknad(fnr = fnr, soknadId = reisetilskudd.first().id)
        val avbruttSøknad = this.hentSoknader(fnr).first()

        avbruttSøknad.status shouldBeEqualTo RSSoknadstatus.AVBRUTT
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        this.gjenapneSoknad(fnr = fnr, soknadId = reisetilskudd.first().id)
        val gjenåpnet = this.hentSoknader(fnr).first()

        gjenåpnet.status shouldBeEqualTo RSSoknadstatus.NY
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        val svaret = this.hentSoknader(fnr).first().sporsmal!!.find { it.tag == ANSVARSERKLARING }!!.svar.first()
        svaret.verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(6)
    fun `Vi kan laste opp en kvittering`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.filter { it.tag == KVITTERINGER }.first()
        val svar = RSSvar(
            verdi = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 133700,
                typeUtgift = Utgiftstype.PARKERING,
                opprettet = Instant.now(),
            ).serialisertTilString(),
            id = null,
        )

        val spmSomBleSvart = lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar).oppdatertSporsmal

        val returnertSvar = spmSomBleSvart.svar.first()

        val returnertKvittering: Kvittering = OBJECT_MAPPER.readValue(returnertSvar.verdi)
        returnertKvittering.typeUtgift.`should be equal to`(Utgiftstype.PARKERING)
    }

    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpm.svar.size `should be equal to` 1

        val returnertSvar = kvitteringSpm.svar.first()
        val returnertKvittering: Kvittering = OBJECT_MAPPER.readValue(returnertSvar.verdi)

        returnertKvittering.blobId.`should be equal to`("9a186e3c-aeeb-4566-a865-15aa9139d364")
        returnertKvittering.belop.`should be equal to`(133700)
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {

        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpm.svar.size `should be equal to` 1

        val svaret = kvitteringSpm.svar.first()

        slettSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svaret.id!!)

        val reisetilskuddSoknadEtter = this.hentSoknader(fnr).first()
        val kvitteringSpmEtter = reisetilskuddSoknadEtter.sporsmal!!.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpmEtter.svar.size `should be equal to` 0
    }

    @Test
    @Order(9)
    fun `Vi laster opp en kvittering igjen`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal!!.filter { it.tag == KVITTERINGER }.first()
        val svar = RSSvar(
            verdi = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 133700,
                typeUtgift = Utgiftstype.PARKERING,
                opprettet = Instant.now(),
            ).serialisertTilString(),
            id = null,
        )

        lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id!!, svar)
    }

    @Test
    @Order(10)
    fun `Vi tester å sende inn søknaden før alle svar er besvart og får bad request`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        this.sendSoknadMedResult(fnr, reisetilskudd.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(11)
    fun `Vi besvarer et spørsmål med feil type verdi`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        val utbetaling = reisetilskudd.sporsmal!!.first { it.tag == UTBETALING }.byttSvar(svar = "TJA")

        val json = oppdaterSporsmalMedResult(fnr, utbetaling, reisetilskudd.id)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn().response.contentAsString
        json shouldBeEqualTo "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(12)
    fun `Vi besvarer resten av spørsmålene`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TRANSPORT_TIL_DAGLIG, "NEI")
            .besvarSporsmal(REISE_MED_BIL, "NEI")
            .besvarSporsmal(UTBETALING, "JA")
            .besvarSporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
    }

    @Test
    @Order(13)
    fun `Vi kan sende inn søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        val sendtSøknad = SoknadBesvarer(reisetilskudd, this, fnr)
            .sendSoknad()
        sendtSøknad.status shouldBeEqualTo RSSoknadstatus.SENDT
    }

    @Test
    @Order(14)
    fun `Vi leser av alt som er produsert`() {
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3)
    }
}
