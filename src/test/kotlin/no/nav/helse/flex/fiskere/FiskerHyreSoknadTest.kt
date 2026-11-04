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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

class FiskerHyreSoknadTest : FellesTestOppsett() {
    @Nested
    inner class MedArbeidsgiver :
        FiskerHyreScenario(
            fnr = "12345678910",
            arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "999888777", orgNavn = "Rederiet"),
        )

    @Nested
    inner class UtenArbeidsgiver :
        FiskerHyreScenario(
            fnr = "12345678913",
            arbeidsgiver = null,
        )

    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    abstract inner class FiskerHyreScenario(
        private val fnr: String,
        private val arbeidsgiver: ArbeidsgiverStatusKafkaDTO?,
    ) {
        @Test
        @Order(1)
        fun `Oppretter fisker hyre søknad`() {
            val soknad =
                sendSykmelding(
                    sykmeldingKafkaMessage(
                        fnr = fnr,
                        arbeidssituasjon = Arbeidssituasjon.FISKER,
                        lottOgHyre = LottOgHyre.HYRE,
                        sykmeldingsperioder =
                            heltSykmeldt(
                                fom = LocalDate.of(2020, 2, 1),
                                tom = LocalDate.of(2020, 2, 15),
                            ),
                    ).let { it.copy(event = it.event.copy(arbeidsgiver = arbeidsgiver)) },
                ).single()

            soknad.type shouldBe SoknadstypeDTO.FISKERE
            soknad.status shouldBe SoknadsstatusDTO.NY
            soknad.arbeidssituasjon shouldBe ArbeidssituasjonDTO.FISKER
            soknad.fiskerLottOgHyre shouldBe FiskerLottOgHyreDTO.HYRE
            soknad.fiskerBlad shouldBe FiskerBladDTO.A
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
                    "ARBEID_UNDERVEIS_100_PROSENT_0",
                    ANDRE_INNTEKTSKILDER_V2,
                    OPPHOLD_UTENFOR_EOS,
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
            finnMottakerAvSoknad(soknadId, fnr).mottaker shouldBe RSMottaker.NAV
        }

        @Test
        @Order(5)
        fun `Søknaden sendes kun til Nav`() {
            val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

            val sendtSoknad =
                SoknadBesvarer(soknad, this@FiskerHyreSoknadTest, fnr)
                    .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
                    .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
                    .besvarSporsmal(FERIE_V2, "NEI")
                    .besvarSporsmal(PERMISJON_V2, "NEI")
                    .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
                    .besvarSporsmal(ANDRE_INNTEKTSKILDER_V2, "NEI")
                    .besvarSporsmal(OPPHOLD_UTENFOR_EOS, "NEI")
                    .oppsummering()
                    .sendSoknad()

            sendtSoknad.status shouldBe RSSoknadstatus.SENDT
            sendtSoknad.sendtTilNAVDato.shouldNotBeNull()
            sendtSoknad.sendtTilArbeidsgiverDato.shouldBeNull()

            val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            kafkaSoknad.type shouldBe SoknadstypeDTO.FISKERE
            kafkaSoknad.status shouldBe SoknadsstatusDTO.SENDT
            kafkaSoknad.arbeidssituasjon shouldBe ArbeidssituasjonDTO.FISKER
            kafkaSoknad.fiskerLottOgHyre shouldBe FiskerLottOgHyreDTO.HYRE
            kafkaSoknad.arbeidsgiver?.orgnummer shouldBe arbeidsgiver?.orgnummer
            kafkaSoknad.sendtNav.shouldNotBeNull()
            kafkaSoknad.sendtArbeidsgiver.shouldBeNull()
            kafkaSoknad.medlemskapVurdering.shouldBeNull()

            hentJuridiskeVurderinger(antall = 1).single().paragraf shouldBe "8-13"
        }
    }
}
