package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleErUtaforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GradertReisetilskuddFrilanserTest : FellesTestOppsett() {
    final val fnr = "123456789"

    val sykmeldingId = UUID.randomUUID().toString()
    val fom = LocalDate.of(2021, 9, 1)
    val tom = LocalDate.of(2021, 9, 20)

    @BeforeAll
    fun `Det er ingen søknader til å begynne med`() {
        hentSoknaderMetadata(fnr).shouldBeEmpty()
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `Vi oppretter en reisetilskuddsøknad`() {
        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.FRILANSER,
                statusEvent = STATUS_BEKREFTET,
            )
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding =
            skapArbeidsgiverSykmelding(
                sykmeldingId = sykmeldingId,
                fom = fom,
                tom = tom,
                type = PeriodetypeDTO.GRADERT,
                gradert = GradertDTO(50, true),
                reisetilskudd = false,
            )

        val sykmeldingKafkaMessage =
            SykmeldingKafkaMessage(
                sykmelding = sykmelding,
                event = sykmeldingStatusKafkaMessageDTO.event,
                kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata,
            )

        mockFlexSyketilfelleErUtaforVentetid(sykmeldingId, true)
        mockFlexSyketilfelleSykeforloep(sykmelding.id)

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC,
        )

        val soknader =
            sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
    }

    @Test
    @Order(2)
    fun `Søknaden har alle spørsmål før vi har svart på om reisetilskuddet ble brukt`() {
        val soknader = hentSoknaderMetadata(fnr)
        assertThat(soknader).hasSize(1)

        val soknaden =
            hentSoknad(
                soknadId = soknader.first().id,
                fnr = fnr,
            )

        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                BRUKTE_REISETILSKUDDET,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(3)
    fun `Vi kan besvare spørsmålet om at reisetilskudd ble brukt og får ikke utbetalings spm`() {
        val reisetilskudd =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        SoknadBesvarer(reisetilskudd, this, fnr)
            .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA", mutert = true)

        val reisetilskuddEtterSvar =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        reisetilskuddEtterSvar
            .sporsmal!!
            .find { it.tag == BRUKTE_REISETILSKUDDET }!!
            .svar
            .first()
            .verdi shouldBeEqualTo "JA"

        assertThat(reisetilskuddEtterSvar.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "JOBBET_DU_GRADERT_0",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                BRUKTE_REISETILSKUDDET,
                TRANSPORT_TIL_DAGLIG,
                REISE_MED_BIL,
                KVITTERINGER,
                TIL_SLUTT,
            ),
        )
    }
}
