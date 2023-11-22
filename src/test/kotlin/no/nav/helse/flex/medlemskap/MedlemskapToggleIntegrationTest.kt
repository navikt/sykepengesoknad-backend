package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

/**
 * Tester at toggle for medlemskapspørsmål fungerer som forventet. Dvs. at det ikke stilles spørsmål om arbeid utenfor
 * Norge når det stilles medlemskapspørsmål.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapToggleIntegrationTest : BaseTestClass() {

    @BeforeEach
    fun setUpMockRessurser() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
        medlemskapMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
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
    }

    @AfterEach
    fun slettFraDatabase() {
        databaseReset.resetDatabase()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    private val fnr = "31111111111"

    @Test
    fun `Ikke still spørsmål om ARBEID_UTENFOR_NORGE når det stilles medlemskapspørsmål`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL)

        val soknader = sendSykmelding()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.first().medlemskapVurdering).isEqualTo("UAVKLART")

        val lagretSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(lagretSoknad.sporsmal!!.map { it.tag }).isEqualTo(
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
    fun `Still spørsmål om ARBEID_UTENFOR_NORGE når det ikke stilles medlemskapspørsmål`() {
        val soknader = sendSykmelding()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.first().medlemskapVurdering).isNull()

        val lagretSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(lagretSoknad.sporsmal!!.map { it.tag }).isEqualTo(
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

    private fun sendSykmelding() = sendSykmelding(
        sykmeldingKafkaMessage(
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            fnr = fnr,
            sykmeldingsperioder = heltSykmeldt(
                fom = LocalDate.of(2023, 1, 1),
                tom = LocalDate.of(2023, 1, 7)
            )
        )
    )
}
