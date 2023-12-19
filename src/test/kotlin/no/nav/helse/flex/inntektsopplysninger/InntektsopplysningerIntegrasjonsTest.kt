package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.kafka.consumer.SYKMELDINGSENDT_TOPIC
import no.nav.helse.flex.mockFlexSyketilfelleErUtaforVentetid
import no.nav.helse.flex.mockFlexSyketilfelleSykeforloep
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.testdata.skapArbeidsgiverSykmelding
import no.nav.helse.flex.testdata.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class InntektsopplysningerIntegrasjonsTest : BaseTestClass() {

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Stiller ikke spørsmål om inntektsopplysnninger på førstegangssøknad når Unleash toggel er disabled`() {
        val fnr = "99999999001"
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC
        )

        val kafkaRecords = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)


        val sykepengesoknad = kafkaRecords.tilSoknader().first()

        sykepengesoknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE


        val lagretSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(lagretSoknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTLAND,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    @Order(2)
    fun `Stiller spørsmål om inntektsopplysnninger på førstegangssøknad når Unleash toggle er enabled`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER)

        val fnr = "99999999002"
        val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
        val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
        val sykmelding = skapArbeidsgiverSykmelding(sykmeldingId = sykmeldingId)
            .copy(harRedusertArbeidsgiverperiode = true)

        mockFlexSyketilfelleErUtaforVentetid(sykmelding.id, true)
        mockFlexSyketilfelleSykeforloep(sykmeldingId)

        val sykmeldingKafkaMessage = SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            event = sykmeldingStatusKafkaMessageDTO.event,
            kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
        )

        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(
            sykmeldingId,
            sykmeldingKafkaMessage,
            SYKMELDINGSENDT_TOPIC
        )

        val kafkaRecords = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)


        val sykepengesoknad = kafkaRecords.tilSoknader().first()

        sykepengesoknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE


        val lagretSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(lagretSoknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                UTLAND,
                INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }
}
