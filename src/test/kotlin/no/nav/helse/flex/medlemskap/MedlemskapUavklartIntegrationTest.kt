package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

/**
 * Tester som verifiserer at flagget "medlemskapVurdering" IKKE er statt på søknader som sendes på Kafka
 * når det IKKE er stilt spørsmål selv om medlemskapvurdering er UAVKLART.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapUavklartIntegrationTest : FellesTestOppsett() {
    @BeforeAll
    fun configureUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Oppretter søknad med status UAVKLART men uten spørsmål fra LovMe som skal ha ARBEID_UTENFOR_NORGE`() {
        val fnr = "31111111116"
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = emptyList(),
                ).serialisertTilString(),
            ),
        )

        val soknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2023, 1, 1),
                            tom = LocalDate.of(2023, 1, 7),
                        ),
                ),
            )

        assertThat(soknader).hasSize(1)
        assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
        assertThat(soknader.last().medlemskapVurdering).isNull()

        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
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
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            ),
        )
    }

    @Test
    @Order(2)
    fun `Sender søknad med status UAVKLART uten spørsmål og sjekker at flagget medlemskapVurdering ikke er satt`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val fnr = "31111111116"
        hentSoknadSomKanBesvares(fnr).let {
            val (_, soknadBesvarer) = it
            besvarArbeidstakerSporsmal(soknadBesvarer)
            val sendtSoknad =
                soknadBesvarer
                    .oppsummering()
                    .sendSoknad()
            sendtSoknad.status shouldBeEqualTo RSSoknadstatus.SENDT
        }

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
        assertThat(kafkaSoknad.medlemskapVurdering).isNull()
    }

    private fun hentSoknadMedStatusNy(fnr: String): RSSykepengesoknad =
        hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr,
        )

    private fun hentSoknadSomKanBesvares(fnr: String): Pair<RSSykepengesoknad, SoknadBesvarer> {
        val soknad = hentSoknadMedStatusNy(fnr)
        val soknadBesvarer = SoknadBesvarer(rSSykepengesoknad = soknad, testOppsettInterfaces = this, fnr = fnr)
        return Pair(soknad, soknadBesvarer)
    }

    private fun besvarArbeidstakerSporsmal(soknadBesvarer: SoknadBesvarer) =
        soknadBesvarer
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
            .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
            .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
            .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
            .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
            .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
            .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
}
