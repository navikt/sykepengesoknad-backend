package no.nav.helse.flex.medlemskap

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.*
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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

    @AfterEach
    fun slettFraDatabase() {
        databaseReset.resetDatabase()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    // Trigger response fra LovMe med alle spørsmål.
    private final val fnr = "31111111111"

    @Test
    fun `Ikke still spørsmål om ARBEID_UTENFOR_NORGE når det stilles medlemskapspørsmål`() {
        whenever(medlemskapToggle.stillMedlemskapSporsmal(fnr = any<String>())).thenReturn(true)

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
                UTLAND_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ANDRE_INNTEKTSKILDER_V2,
                MEDLEMSKAP_OPPHOLDSTILLATELSE,
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
    }

    @Test
    fun `Still spørsmål om ARBEID_UTENFOR_NORGE når det ikke stilles medlemskapspørsmål`() {
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
                UTLAND_V2,
                medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0),
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER_V2,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
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