package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MedlemskapSporsmalIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    // Trigger response fra LovMe med alle spørsmål.
    private final val fnr = "31111111111"

    @AfterAll
    fun cleanUp() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Oppretter arbeidstakersøknad med status NY`() {
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

        Assertions.assertThat(soknader).hasSize(1)
        Assertions.assertThat(soknader.last().type).isEqualTo(SoknadstypeDTO.ARBEIDSTAKERE)
        Assertions.assertThat(soknader.last().status).isEqualTo(SoknadsstatusDTO.NY)
    }

    @Test
    @Order(2)
    fun `Verifiser at søknaden har spørsmål som forventet`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        Assertions.assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER_V2",
                "MEDLEMSKAP_OPPHOLDSTILLATELSE",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )
    }

    @Test
    @Order(3)
    fun `Response fra LovMed medlemskapvurdering er lagret i databasen`() {
        val medlemskapVurderingDbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val medlemskapVurdering = medlemskapVurderingDbRecords.first()

        medlemskapVurdering.fnr `should be equal to` fnr
        medlemskapVurdering.svartype `should be equal to` MedlemskapVurderingSvarType.UAVKLART.toString()
        medlemskapVurdering.sporsmal!!.value `should be equal to` listOf(
            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
        ).serialisertTilString()
    }

    @Test
    @Order(4)
    fun `Spørsmål er riktig sortert`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val indexOfMedlemskapOppholdstillatelse =
            soknad.sporsmal!!.indexOf(soknad.sporsmal!!.first { it.tag == "MEDLEMSKAP_OPPHOLDSTILLATELSE" }) shouldBeEqualTo 8
        soknad.sporsmal!![indexOfMedlemskapOppholdstillatelse - 1].tag shouldBeEqualTo "ANDRE_INNTEKTSKILDER_V2"
        soknad.sporsmal!![indexOfMedlemskapOppholdstillatelse + 1].tag shouldBeEqualTo "VAER_KLAR_OVER_AT"
    }

    @Test
    @Order(4)
    fun `Besvar medlemskapspørsmål og send inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val sendtSoknad = besvarArbeidstakersporsmal(soknad)
            .besvarSporsmal(tag = "MEDLEMSKAP_OPPHOLDSTILLATELSE", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO",
                svar = soknad.fom.toString(),
                ferdigBesvart = false
            )
            .besvarSporsmal(tag = "MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT", svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE",
                svar = DatoUtil.periodeTilJson(
                    fom = soknad.tom!!.minusDays(25),
                    tom = soknad.tom!!.minusDays(5)
                )
            )
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()

        sendtSoknad.status shouldBeEqualTo RSSoknadstatus.SENDT

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT

        // Medlemsskapspørsmål blir ikke mappet om til eget felt i SykepengesoknadDTO så vi trenger bare å sjekke at
        // spørsmålet er med.
        kafkaSoknad.sporsmal!!.any { it.tag == "MEDLEMSKAP_OPPHOLDSTILLATELSE" } shouldBeEqualTo true
    }

    private fun besvarArbeidstakersporsmal(soknaden: RSSykepengesoknad) =
        SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
}
