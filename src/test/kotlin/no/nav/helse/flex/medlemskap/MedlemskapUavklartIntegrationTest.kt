package no.nav.helse.flex.medlemskap

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER_V2
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UNDERVEIS_100_PROSENT
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FERIE_V2
import no.nav.helse.flex.soknadsopprettelse.PERMISJON_V2
import no.nav.helse.flex.soknadsopprettelse.TILBAKE_I_ARBEID
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

/**
 * Tester at flagget medlemskapVurdering ikke blir satt på søkand som sendes på Kafak når LovMe returnerer
 * UAVKLART men samtidig en tom liste med spørsmål. Dette er en midlertidig test da UAVKLART alltid skal
 * returnere spørsmål.
 *
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapUavklartIntegrationTest : BaseTestClass() {

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status UAVKLART`() {
        whenever(medlemskapToggle.stillMedlemskapSporsmal(fnr = any<String>())).thenReturn(true)

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                // Trigger response fra LovMe med status UAVKLART.
                fnr = "31111111111",
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
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status UAVKLART men ikke spørsmål`() {
        whenever(medlemskapToggle.stillMedlemskapSporsmal(fnr = any<String>())).thenReturn(true)

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                // Trigger response fra LovMe med status UAVKLART uten spørsmål.
                fnr = "31111111116",
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
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status JA`() {
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                // Trigger response fra LovMe med status JA.
                fnr = "31111111112",
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
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status NEI`() {
        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                // Trigger response fra LovMe med status NEI.
                fnr = "31111111113",
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
    }

    @Test
    @Order(2)
    fun `Besvar arbeidstakerspørsmål og sender søknaden med status UAVKLART men uten spørsmål`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        hentSoknadSomKanBesvares("31111111116").let {
            val (_, soknadBesvarer) = it
            besvarArbeidstakerSporsmal(soknadBesvarer)
            val sendtSoknad = soknadBesvarer
                .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
                .sendSoknad()
            sendtSoknad.status shouldBeEqualTo RSSoknadstatus.SENDT
        }

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
        assertThat(kafkaSoknad.medlemskapVurdering).isNull()
    }

    private fun hentSoknadMedStatusNy(fnr: String): RSSykepengesoknad {
        return hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )
    }

    private fun hentSoknadSomKanBesvares(fnr: String): Pair<RSSykepengesoknad, SoknadBesvarer> {
        val soknad = hentSoknadMedStatusNy(fnr)
        val soknadBesvarer = SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
        return Pair(soknad, soknadBesvarer)
    }

    private fun besvarArbeidstakerSporsmal(soknadBesvarer: SoknadBesvarer) =
        soknadBesvarer
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
            .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
            .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
            .besvarSporsmal(tag = UTLAND_V2, svar = "NEI")
            .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
            .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
}
