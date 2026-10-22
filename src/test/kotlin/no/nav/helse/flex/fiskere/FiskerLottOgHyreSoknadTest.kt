package no.nav.helse.flex.fiskere

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
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerBladDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FiskerLottOgHyreDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.LottOgHyre
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

class FiskerLottOgHyreSoknadTest : FellesTestOppsett() {
    @Nested
    inner class MedArbeidsgiver :
        FiskerLottOgHyreScenario(
            fnr = "12345678910",
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "999888777", orgNavn = "Rederiet"),
        )

    @Nested
    inner class UtenArbeidsgiver :
        FiskerLottOgHyreScenario(
            fnr = "12345678913",
            arbeidsgiver = null,
        )

    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    abstract inner class FiskerLottOgHyreScenario(
        private val fnr: String,
        private val arbeidsgiver: ArbeidsgiverStatusKafkaDTO?,
    ) {
        @Test
        @Order(1)
        fun `Oppretter fisker lott og hyre søknad`() {
            val soknad =
                sendSykmelding(
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        arbeidssituasjon = Arbeidssituasjon.FISKER,
                        lottOgHyre = LottOgHyre.BEGGE,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2020, 2, 1),
                                tom = LocalDate.of(2020, 2, 15),
                            ),
                    ).let { it.copy(event = it.event.copy(arbeidsgiver = arbeidsgiver)) },
                ).single()

            soknad.type `should be equal to` SoknadstypeDTO.FISKERE
            soknad.status `should be equal to` SoknadsstatusDTO.NY
            soknad.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.FISKER
            soknad.fiskerLottOgHyre `should be equal to` FiskerLottOgHyreDTO.BEGGE
            soknad.fiskerBlad `should be equal to` FiskerBladDTO.A
            soknad.arbeidsgiver?.orgnummer `should be equal to` arbeidsgiver?.orgnummer
            soknad.medlemskapVurdering.shouldBeNull()
        }

        @Test
        @Order(2)
        fun `Søknaden har arbeidstakerspørsmål`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            soknad.sporsmal!!.map { it.tag } `should be equal to`
                listOf(
                    ANSVARSERKLARING,
                    TILBAKE_I_ARBEID,
                    FERIE_V2,
                    PERMISJON_V2,
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    FISKER_LOTT_OPPRETTHOLDT_INNTEKT,
                    ANDRE_INNTEKTSKILDER_V2,
                    OPPHOLD_UTENFOR_EOS,
                    NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                    NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
                    NARINGSDRIVENDE_VARIG_ENDRING,
                    TIL_SLUTT,
                )
        }

        @Test
        @Order(3)
        fun `Spørsmålstekstene nevner arbeidsgiver kun når den er oppgitt`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)
            val tilbakeIArbeid = soknad.getSporsmalMedTag(TILBAKE_I_ARBEID).sporsmalstekst!!
            val arbeidUnderveis = soknad.getSporsmalMedTag("ARBEID_UNDERVEIS_100_PROSENT_0").sporsmalstekst!!

            val arbeidsgiverNavn = soknad.arbeidsgiver?.navn
            if (arbeidsgiverNavn != null) {
                tilbakeIArbeid shouldContain "hos $arbeidsgiverNavn"
                arbeidUnderveis shouldContain "hos $arbeidsgiverNavn"
            } else {
                tilbakeIArbeid shouldNotContain " hos "
                arbeidUnderveis shouldNotContain " hos "
            }
        }

        @Test
        @Order(4)
        fun `Mottaker av søknaden er kun Nav`() {
            val soknadId = hentSoknaderMetadata(fnr).single().id
            finnMottakerAvSoknad(soknadId, fnr).mottaker `should be equal to` RSMottaker.NAV
        }

        @Test
        @Order(5)
        fun `Søknaden sendes kun til Nav`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            val sendtSoknad =
                SoknadBesvarer(soknad, this@FiskerLottOgHyreSoknadTest, fnr)
                    .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
                    .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
                    .besvarSporsmal(FERIE_V2, "NEI")
                    .besvarSporsmal(PERMISJON_V2, "NEI")
                    .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
                    .besvarSporsmal(FISKER_LOTT_OPPRETTHOLDT_INNTEKT, "NEI")
                    .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
                    .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET, "NEI")
                    .besvarSporsmal(NARINGSDRIVENDE_VARIG_ENDRING, "NEI")
                    .oppsummering()
                    .sendSoknad()

            sendtSoknad.status `should be equal to` RSSoknadstatus.SENDT
            sendtSoknad.sendtTilNAVDato.shouldNotBeNull()
            sendtSoknad.sendtTilArbeidsgiverDato.shouldBeNull()

            val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            kafkaSoknad.type `should be equal to` SoknadstypeDTO.FISKERE
            kafkaSoknad.status `should be equal to` SoknadsstatusDTO.SENDT
            kafkaSoknad.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.FISKER
            kafkaSoknad.fiskerLottOgHyre `should be equal to` FiskerLottOgHyreDTO.BEGGE
            kafkaSoknad.arbeidsgiver?.orgnummer `should be equal to` arbeidsgiver?.orgnummer
            kafkaSoknad.sendtNav.shouldNotBeNull()
            kafkaSoknad.sendtArbeidsgiver.shouldBeNull()
            kafkaSoknad.medlemskapVurdering.shouldBeNull()

            hentJuridiskeVurderinger(antall = 1).single().paragraf `should be equal to` "8-13"
        }
    }

    @Test
    fun `Søknad nummer to i samme sykeforlop får ikke førstegangsspørsmål for næringsdrivende`() {
        val fnr = "12345678915"
        val oppfolgingsdato = LocalDate.of(2020, 2, 1)

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = "1",
                arbeidssituasjon = Arbeidssituasjon.FISKER,
                lottOgHyre = LottOgHyre.BEGGE,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 1),
                        tom = LocalDate.of(2020, 2, 15),
                    ),
            ),
            oppfolgingsdato = oppfolgingsdato,
        ).single()

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = "2",
                arbeidssituasjon = Arbeidssituasjon.FISKER,
                lottOgHyre = LottOgHyre.BEGGE,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 16),
                        tom = LocalDate.of(2020, 2, 29),
                    ),
            ),
            oppfolgingsdato = oppfolgingsdato,
            sykmeldingIderMedSammeVentetid = setOf("1"),
        ).single()

        val soknader = hentSoknaderMetadata(fnr).sortedBy { it.fom }
        val forsteSoknad = hentSoknad(soknadId = soknader.first().id, fnr = fnr)
        val andreSoknad = hentSoknad(soknadId = soknader.last().id, fnr = fnr)

        val forsteTags = forsteSoknad.sporsmal!!.map { it.tag }
        val andreTags = andreSoknad.sporsmal!!.map { it.tag }

        listOf(
            NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
            NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
            NARINGSDRIVENDE_NY_I_ARBEIDSLIVET,
            NARINGSDRIVENDE_VARIG_ENDRING,
        ).forEach { tag ->
            forsteTags.contains(tag) `should be equal to` true
            andreTags.contains(tag) `should be equal to` false
        }
    }
}
