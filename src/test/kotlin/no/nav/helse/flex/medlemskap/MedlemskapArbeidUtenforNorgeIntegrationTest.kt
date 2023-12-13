package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

/**
 * Tester som verifiserer at spørsmålet om ARBEID_UTENFOR_NORGE blir stilt når
 * det ikke stilles erstattende medlemskapspørsmål.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapArbeidUtenforNorgeIntegrationTest : BaseTestClass() {

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL, UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status UAVKLART som ikke skal ha ARBEID_UTENFOR_NORGE`() {
        val fnr = "31111111111"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            )
        )

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("UAVKLART")

        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ANDRE_INNTEKTSKILDER_V2,
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                UTLAND_V2,
                MEDLEMSKAP_OPPHOLDSTILLATELSE,
                TIL_SLUTT
            )
        )
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status UAVKLART uten spørsmål fra LovMe som skal ha ARBEID_UTENFOR_NORGE`() {
        val fnr = "31111111116"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = emptyList()
                ).serialisertTilString()
            )
        )

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isNull()

        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT
            )
        )
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status JA som ikke skal ha ARBEID_UTENFOR_NORGE`() {
        val fnr = "31111111112"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList()
                ).serialisertTilString()
            )
        )

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("JA")

        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT
            )
        )
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status NEI som ikke skal ha ARBEID_UTENFOR_NORGE`() {
        val fnr = "31111111113"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = emptyList()
                ).serialisertTilString()
            )
        )

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = LocalDate.of(2023, 1, 1),
                    tom = LocalDate.of(2023, 1, 7)
                )
            )
        )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("NEI")

        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ANDRE_INNTEKTSKILDER_V2,
                UTLAND_V2,
                TIL_SLUTT
            )
        )
    }
}
