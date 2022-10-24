package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockFlexSyketilfelleErUtaforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.BRUKTE_REISETILSKUDDET
import no.nav.helse.flex.soknadsopprettelse.KVITTERINGER
import no.nav.helse.flex.soknadsopprettelse.REISE_MED_BIL
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.TRANSPORT_TIL_DAGLIG
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.getSykmeldingDto
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GradertReisetilskuddFrilanserTest : BaseTestClass() {

    final val fnr = "123456789"

    val sykmeldingId = UUID.randomUUID().toString()
    val fom = LocalDate.of(2021, 9, 1)
    val tom = LocalDate.of(2021, 9, 20)

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
            arbeidssituasjon = Arbeidssituasjon.FRILANSER,
            statusEvent = STATUS_BEKREFTET
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

        mockFlexSyketilfelleErUtaforVentetid(sykmeldingId, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(sykmeldingId, sykmeldingKafkaMessage)

        val soknader =
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
    }

    @Test
    @Order(2)
    fun `02 - søknaden har alle spørsmål før vi har svart på om reisetilskuddet ble brukt`() {
        val soknader = hentSoknader(fnr)
        assertThat(soknader).hasSize(1)
        val soknaden = soknader.first()

        assertThat(soknaden.sporsmal!!.first { it.tag == VAER_KLAR_OVER_AT }.undertekst).contains("sykepenger og reisetilskudd")
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTLAND,
                UTDANNING,
                BRUKTE_REISETILSKUDDET,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(3)
    fun `Vi kan besvare spørsmålet om at reisetilskudd ble brukt og får ikke utbetalings spm`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA", mutert = true)

        val reisetilskuddEtterSvar = this.hentSoknader(fnr).first()
        reisetilskuddEtterSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar.first().verdi shouldBeEqualTo "JA"

        assertThat(reisetilskuddEtterSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTLAND,
                UTDANNING,
                BRUKTE_REISETILSKUDDET,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }
}
