package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.behandingsdager
import no.nav.helse.flex.testdata.gradertReisetilskudd
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.reisetilskudd
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.internal.assertEquals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Tester at medlemskapspørsmål blir stilt på riktig søkandstype.
 *
 * LovMe lytter på og prosesserer Soknadstype.ARBEIDSTAKERE.
 * Flex lager medlemskapsspørsmål på Arbeidssituasjon.ARBEIDSTAKER som inkluderer søknadstypene
 * GRADERT_REISETILSKUDD og ARBEIDSTAKERE.
 *
 * Soknadstypene BEHANDLINGSDAGER og REISETILSKUDD tilhører også Arbeidssituasjon.ARBEIDSTAKER men disse blir
 * filtert bort før det genereres medlemskapsspørsmål.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapSoknadstypeIntegrationTest : BaseTestClass() {

    @BeforeAll
    fun configureUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_MEDLEMSKAP_SPORSMAL, UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
        sykepengesoknadKafkaConsumer.hentProduserteRecords()
    }

    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 7)

    @Test
    @Order(1)
    fun `Soknadstype ARBEIDSTAKERE skal ha medlemskapspørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            )
        )

        val fnr = "31111111111"
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = fom,
                    tom = tom
                )
            )
        )

        assertEquals(fnr, medlemskapMockWebServer.takeRequest().headers["fnr"])

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("UAVKLART")
    }

    @Test
    @Order(2)
    fun `Soknadstype GRADERT_REISETILSKUDD skal ha medlemskapspørsmål`() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            )
        )

        val fnr = "31111111112"
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = gradertReisetilskudd(
                    fom = fom,
                    tom = tom
                )
            )
        )

        assertEquals(fnr, medlemskapMockWebServer.takeRequest().headers["fnr"])

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.GRADERT_REISETILSKUDD)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isEqualTo("UAVKLART")
    }

    @Test
    @Order(3)
    fun `Soknadstype BEHANDLINGSDAGER skal ikke ha medlemskapspørsmål`() {
        val fnr = "31111111113"
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = behandingsdager(
                    fom = fom,
                    tom = tom
                )
            )
        )

        assertThat(medlemskapMockWebServer.takeRequest(500, TimeUnit.MILLISECONDS)).isNull()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.BEHANDLINGSDAGER)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isNull()
    }

    @Test
    @Order(4)
    fun `Soknadstype REISETILSKUDD skal ikke ha medlemskapspørsmål`() {
        val fnr = "31111111114"
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                fnr = fnr,
                sykmeldingsperioder = reisetilskudd(
                    fom = fom,
                    tom = tom
                )
            )
        )

        assertThat(medlemskapMockWebServer.takeRequest(500, TimeUnit.MILLISECONDS)).isNull()

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.REISETILSKUDD)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isNull()
    }
}
