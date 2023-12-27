package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER
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

    private val fom = LocalDate.of(2023, 1, 1)
    private val tom = LocalDate.of(2023, 1, 30)

    @Test
    @Order(1)
    fun `Stiller ikke spørsmål om inntektsopplysnninger på førstegangssøknad når Unleash toggle er disabled`() {
        val fnr = "99999999001"

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = fom,
                    tom = tom
                )
            )
        )

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
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
    @Order(1)
    fun `Stiller spørsmål om inntektsopplysnninger på førstegangssøknad når Unleash toggle er enabled`() {
        fakeUnleash.enable(UNLEASH_CONTEXT_NARINGSDRIVENDE_INNTEKTSOPPLYSNINGER)

        val fnr = "99999999002"

        val soknader = sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = fom,
                    tom = tom
                )
            )
        )

        soknader shouldHaveSize 1
        val soknad = soknader.first()
        soknad.arbeidssituasjon shouldBeEqualTo ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
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

    @Test
    @Order(2)
    fun `Besvar og sendt inn søknad med inntektsopplysninger som ny i arbeidslivet`() {
        val fnr = "99999999002"
        val lagretSoknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        hentSoknadSomKanBesvares(fnr).let {
            val (_, soknadBesvarer) = it
            besvarStandardsporsmalSporsmal(soknadBesvarer)
            val sendtSoknad = soknadBesvarer
                .besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA,
                    svar = "CHECKED",
                    ferdigBesvart = false
                )
                .besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_DATO,
                    svar = lagretSoknad.fom!!.minusDays(1).toString()
                )
                .besvarSporsmal(tag = VAER_KLAR_OVER_AT, svar = "Svar", ferdigBesvart = false)
                .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
                .sendSoknad()
            sendtSoknad.status shouldBeEqualTo RSSoknadstatus.SENDT

            sendtSoknad.inntektsopplysningerNyKvittering shouldBeEqualTo true
            sendtSoknad.inntektsopplysningerInnsendingDokumenter!!.isNotEmpty() shouldBeEqualTo true
            sendtSoknad.inntektsopplysningerInnsendingId shouldBeEqualTo "TODO"
        }

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
    }

    @Test
    @Order(3)
    fun `Korrigerer til ikke ny i arbeidslivet og mindre enn 25 så det ikke trengs dokumentasjon`() {
        val fnr = "99999999002"
        val soknad = hentSoknader(fnr).first()
        val korrigerendeSoknad = korrigerSoknad(soknad.id, fnr)

        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknad.id)

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            // Nullstiller det andre alternative i Checkbox-gruppen, sånn at gruppen ikke validerer på grunn av to svar.
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA,
                svar = null,
                ferdigBesvart = false
            )
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
                svar = "CHECKED",
                ferdigBesvart = false
            ).besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING,
                svar = "JA",
                ferdigBesvart = false
            ).besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OPPRETTELSE_NEDLEGGELSE,
                svar = "CHECKED",
                ferdigBesvart = false
            ).besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ANNET,
                svar = "CHECKED",
                ferdigBesvart = false
            ).besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT,
                svar = "NEI"
            )
            .besvarSporsmal(tag = VAER_KLAR_OVER_AT, svar = "Svar", ferdigBesvart = false)
            .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
            .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        sendtSoknad.inntektsopplysningerNyKvittering shouldBeEqualTo true
        sendtSoknad.inntektsopplysningerInnsendingDokumenter shouldBeEqualTo null
        sendtSoknad.inntektsopplysningerInnsendingId shouldBeEqualTo null

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.id shouldBeEqualTo korrigerendeSoknad.id
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
    }

    @Test
    @Order(4)
    fun `Korrigerer til søknad med større endring enn 25 prosent`() {
        val fnr = "99999999002"
        val soknad = hentSoknader(fnr).first { it.status == RSSoknadstatus.SENDT }
        val korrigerendeSoknad = korrigerSoknad(soknad.id, fnr)

        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknad.id)

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT,
                svar = "JA",
                ferdigBesvart = false
            ).besvarSporsmal(
                tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING_DATO,
                svar = soknad.fom!!.minusYears(3).toString()
            )
            .besvarSporsmal(tag = VAER_KLAR_OVER_AT, svar = "Svar", ferdigBesvart = false)
            .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
            .sendSoknad()

        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        sendtSoknad.inntektsopplysningerNyKvittering shouldBeEqualTo true
        sendtSoknad.inntektsopplysningerInnsendingDokumenter!!.isNotEmpty() shouldBeEqualTo true
        sendtSoknad.inntektsopplysningerInnsendingId shouldBeEqualTo "TODO"

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader shouldHaveSize 1
        val kafkaSoknad = kafkaSoknader.first()

        kafkaSoknad.id shouldBeEqualTo korrigerendeSoknad.id
        kafkaSoknad.status shouldBeEqualTo SoknadsstatusDTO.SENDT
    }

    private fun hentSoknadSomKanBesvares(fnr: String): Pair<RSSykepengesoknad, SoknadBesvarer> {
        val soknad = hentSoknadMedStatusNy(fnr)
        val soknadBesvarer = SoknadBesvarer(rSSykepengesoknad = soknad, mockMvc = this, fnr = fnr)
        return Pair(soknad, soknadBesvarer)
    }

    private fun hentSoknadMedStatusNy(fnr: String): RSSykepengesoknad {
        return hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )
    }

    private fun besvarStandardsporsmalSporsmal(soknadBesvarer: SoknadBesvarer) =
        soknadBesvarer
            .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
            .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
            .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
            .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
            .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
            .besvarSporsmal(tag = UTLAND, svar = "NEI")
}