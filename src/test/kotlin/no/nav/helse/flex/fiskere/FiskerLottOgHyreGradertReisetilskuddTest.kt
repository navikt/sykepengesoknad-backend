package no.nav.helse.flex.fiskere

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSMottaker
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.finnMottakerAvSoknad
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerLottOgHyreDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.gradertReisetilskudd
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.LottOgHyre
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

class FiskerLottOgHyreGradertReisetilskuddTest : FellesTestOppsett() {
    @Nested
    inner class MedArbeidsgiver :
        FiskerLottOgHyreGradertReisetilskuddScenario(
            fnr = "12345678912",
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "999888777", orgNavn = "Rederiet"),
        )

    @Nested
    inner class UtenArbeidsgiver :
        FiskerLottOgHyreGradertReisetilskuddScenario(
            fnr = "12345678914",
            arbeidsgiver = null,
        )

    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    abstract inner class FiskerLottOgHyreGradertReisetilskuddScenario(
        private val fnr: String,
        private val arbeidsgiver: ArbeidsgiverStatusKafkaDTO?,
    ) {
        @Test
        @Order(1)
        fun `Oppretter gradert reisetilskudd søknad for fisker lott og hyre`() {
            val soknad =
                sendSykmelding(
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        arbeidssituasjon = Arbeidssituasjon.FISKER,
                        lottOgHyre = LottOgHyre.BEGGE,
                        sykmeldingsperioder =
                            gradertReisetilskudd(
                                fom = LocalDate.of(2020, 2, 1),
                                tom = LocalDate.of(2020, 2, 15),
                            ),
                    ).let { it.copy(event = it.event.copy(arbeidsgiver = arbeidsgiver)) },
                ).single()

            soknad.type shouldBe SoknadstypeDTO.GRADERT_REISETILSKUDD
            soknad.status shouldBe SoknadsstatusDTO.NY
            soknad.arbeidssituasjon shouldBe ArbeidssituasjonDTO.FISKER
            soknad.fiskerLottOgHyre shouldBe FiskerLottOgHyreDTO.BEGGE
            soknad.arbeidsgiver?.orgnummer shouldBe arbeidsgiver?.orgnummer
            soknad.medlemskapVurdering.shouldBeNull()
        }

        @Test
        @Order(2)
        fun `Søknaden har arbeidstakerspørsmål`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            soknad.sporsmal!!.map { it.tag } shouldBe
                listOf(
                    ANSVARSERKLARING,
                    TILBAKE_I_ARBEID,
                    FERIE_V2,
                    PERMISJON_V2,
                    "JOBBET_DU_GRADERT_0",
                    FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT,
                    ANDRE_INNTEKTSKILDER_V2,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    BRUKTE_REISETILSKUDDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
                    TIL_SLUTT,
                )
        }

        @Test
        @Order(3)
        fun `Spørsmålsteksten om arbeid underveis nevner arbeidsgiver kun når den er oppgitt`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)
            val jobbetDuGradert = soknad.getSporsmalMedTag("JOBBET_DU_GRADERT_0").sporsmalstekst!!

            val arbeidsgiverNavn = soknad.arbeidsgiver?.navn
            if (arbeidsgiverNavn != null) {
                jobbetDuGradert shouldContain "hos $arbeidsgiverNavn"
            } else {
                jobbetDuGradert shouldNotContain " hos "
            }
        }

        @Test
        @Order(4)
        fun `Svar ja på brukte reisetilskuddet gir reisetilskuddspørsmål uten utbetalingsspørsmål`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            SoknadBesvarer(soknad, this@FiskerLottOgHyreGradertReisetilskuddTest, fnr)
                .besvarSporsmal(BRUKTE_REISETILSKUDDET, "JA", mutert = true)

            hentSoknad(soknadId = soknad.id, fnr = fnr).sporsmal!!.map { it.tag } shouldBe
                listOf(
                    ANSVARSERKLARING,
                    TILBAKE_I_ARBEID,
                    FERIE_V2,
                    PERMISJON_V2,
                    "JOBBET_DU_GRADERT_0",
                    FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT,
                    ANDRE_INNTEKTSKILDER_V2,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    BRUKTE_REISETILSKUDDET,
                    TRANSPORT_TIL_DAGLIG,
                    REISE_MED_BIL,
                    KVITTERINGER,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
                    TIL_SLUTT,
                )
        }

        @Test
        @Order(5)
        fun `Mottaker av søknaden er kun Nav`() {
            val soknadId = hentSoknaderMetadata(fnr).single().id
            finnMottakerAvSoknad(soknadId, fnr).mottaker shouldBe RSMottaker.NAV
        }

        @Test
        @Order(6)
        fun `Søknaden sendes kun til Nav`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            val sendtSoknad =
                SoknadBesvarer(soknad, this@FiskerLottOgHyreGradertReisetilskuddTest, fnr)
                    .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
                    .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
                    .besvarSporsmal(FERIE_V2, "NEI")
                    .besvarSporsmal(PERMISJON_V2, "NEI")
                    .besvarSporsmal("JOBBET_DU_GRADERT_0", "NEI")
                    .besvarSporsmal(FISKER_LOTT_OPPRETTHOLDT_INNTEKT_GRADERT, "NEI")
                    .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
                    .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, "NEI")
                    .besvarSporsmal(TRANSPORT_TIL_DAGLIG, "NEI")
                    .besvarSporsmal(REISE_MED_BIL, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "NEI")
                    .oppsummering()
                    .sendSoknad()

            sendtSoknad.status shouldBe RSSoknadstatus.SENDT
            sendtSoknad.sendtTilNAVDato.shouldNotBeNull()
            sendtSoknad.sendtTilArbeidsgiverDato.shouldBeNull()

            val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            kafkaSoknad.type shouldBe SoknadstypeDTO.GRADERT_REISETILSKUDD
            kafkaSoknad.status shouldBe SoknadsstatusDTO.SENDT
            kafkaSoknad.arbeidssituasjon shouldBe ArbeidssituasjonDTO.FISKER
            kafkaSoknad.fiskerLottOgHyre shouldBe FiskerLottOgHyreDTO.BEGGE
            kafkaSoknad.arbeidsgiver?.orgnummer shouldBe arbeidsgiver?.orgnummer
            kafkaSoknad.sendtNav.shouldNotBeNull()
            kafkaSoknad.sendtArbeidsgiver.shouldBeNull()
            kafkaSoknad.medlemskapVurdering.shouldBeNull()

            hentJuridiskeVurderinger(antall = 1).single().paragraf shouldBe "8-13"
        }
    }
}
