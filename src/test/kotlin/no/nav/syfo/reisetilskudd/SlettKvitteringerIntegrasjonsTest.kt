package no.nav.syfo.reisetilskudd

import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.syfo.BaseTestClass
import no.nav.syfo.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.syfo.controller.domain.sykepengesoknad.RSSvar
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Kvittering
import no.nav.syfo.domain.Utgiftstype
import no.nav.syfo.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.syfo.hentSoknader
import no.nav.syfo.lagreSvar
import no.nav.syfo.mockFlexSyketilfelleSykeforloep
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.slettSvar
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.ANSVARSERKLARING
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.syfo.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.syfo.soknadsopprettelse.BRUKTE_REISETILSKUDDET
import no.nav.syfo.soknadsopprettelse.FERIE_V2
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.syfo.soknadsopprettelse.KVITTERINGER
import no.nav.syfo.soknadsopprettelse.PERMISJON_V2
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA
import no.nav.syfo.soknadsopprettelse.PERMITTERT_PERIODE
import no.nav.syfo.soknadsopprettelse.REISE_MED_BIL
import no.nav.syfo.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.syfo.soknadsopprettelse.TRANSPORT_TIL_DAGLIG
import no.nav.syfo.soknadsopprettelse.UTBETALING
import no.nav.syfo.soknadsopprettelse.UTDANNING
import no.nav.syfo.soknadsopprettelse.UTLAND_V2
import no.nav.syfo.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.syfo.testdata.getSykmeldingDto
import no.nav.syfo.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.syfo.testutil.SoknadBesvarer
import no.nav.syfo.tilSoknader
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.serialisertTilString
import no.nav.syfo.ventPåRecords
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SlettKvitteringerIntegrasjonsTest : BaseTestClass() {

    private final val fnr = "01017012345"

    @BeforeEach
    fun setUp() {
        whenever(bucketUploaderClient.slettKvittering(any())).thenReturn(true)
    }

    @Test
    @Order(1)
    fun `Det finnes ingen søknader`() {
        val soknader = this.hentSoknader(fnr)
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(2)
    fun `Opprett en søkand med gradert reisetilskudd`() {
        val fom: LocalDate = LocalDate.of(2022, 1, 1)
        val tom: LocalDate = LocalDate.of(2022, 1, 6)

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
            type = PeriodetypeDTO.GRADERT,
            gradert = GradertDTO(50, true),
            reisetilskudd = false,
        )

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSendtBekreftetSykmeldingService.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader =
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(60)).tilSoknader()
        Assertions.assertThat(soknader).hasSize(1)
        Assertions.assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
    }

    @Test
    @Order(3)
    fun `Besvarer spørsmålet om at reisetilskudd ble brukt`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA")

        val reisetilskuddEtterSvar = this.hentSoknader(fnr).first()
        reisetilskuddEtterSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar.first().verdi shouldBeEqualTo "JA"

        Assertions.assertThat(reisetilskuddEtterSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                UTLAND_V2,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTDANNING,
                PERMITTERT_NAA,
                PERMITTERT_PERIODE,
                BRUKTE_REISETILSKUDDET,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                UTBETALING,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(4)
    fun `Laster opp to kvitteringer`() {
        val (soknadId, kvitteringSpm) = hentKvitteringSpm()

        val forsteSvar = RSSvar(
            verdi = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 100,
                typeUtgift = Utgiftstype.PARKERING,
                opprettet = Instant.now(),
            ).serialisertTilString(),
            id = null,
        )

        val andreSvar = RSSvar(
            verdi = Kvittering(
                blobId = "1b186e3c-aeeb-4566-a865-15aa9139d365",
                belop = 200,
                typeUtgift = Utgiftstype.TAXI,
                opprettet = Instant.now(),
            ).serialisertTilString(),
            id = null,
        )

        lagreSvar(fnr, soknadId, kvitteringSpm.id!!, forsteSvar)
        lagreSvar(fnr, soknadId, kvitteringSpm.id!!, andreSvar)

        val (_, lagretSpm) = hentKvitteringSpm()

        lagretSpm.svar.size `should be` 2

        val forsteLagretSvar = lagretSpm.svar.first()
        val forsteKvittering: Kvittering = OBJECT_MAPPER.readValue(forsteLagretSvar.verdi)
        forsteKvittering.typeUtgift.`should be equal to`(Utgiftstype.PARKERING)

        val andreLagretSvar = lagretSpm.svar.drop(1).first()
        val andreKvittering: Kvittering = OBJECT_MAPPER.readValue(andreLagretSvar.verdi)
        andreKvittering.typeUtgift.`should be equal to`(Utgiftstype.TAXI)
    }

    @Test
    @Order(5)
    fun `Sletter begge kvitteringer uten å laste søknad på nytt`() {
        val (soknadId, lagretSpm) = hentKvitteringSpm()
        lagretSpm.svar.size `should be` 2

        val forsteSvar = lagretSpm.svar.first()
        val andreSvar = lagretSpm.svar.drop(1).first()

        slettSvar(fnr, soknadId, lagretSpm.id!!, forsteSvar.id!!)
        slettSvar(fnr, soknadId, lagretSpm.id!!, andreSvar.id!!)
    }

    private fun hentKvitteringSpm(): Pair<String, RSSporsmal> {
        val soknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = soknad.sporsmal!!.first { it.tag == KVITTERINGER }
        return Pair(soknad.id, kvitteringSpm)
    }
}
