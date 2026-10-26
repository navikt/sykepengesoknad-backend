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
import no.nav.syfo.sykmelding.kafka.model.LottOgHyre
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FiskerLottSoknadTest : FellesTestOppsett() {
    private val fnr = "12345678920"
    private val forsteSykmeldingId = "fisker-lott-1"
    private val andreSykmeldingId = "fisker-lott-2"

    @Test
    @Order(1)
    fun `Oppretter fisker lott søknad`() {
        val soknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingId = forsteSykmeldingId,
                    arbeidssituasjon = Arbeidssituasjon.FISKER,
                    lottOgHyre = LottOgHyre.LOTT,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = LocalDate.of(2020, 2, 1),
                            tom = LocalDate.of(2020, 2, 15),
                        ),
                ),
            ).single()

        soknad.type `should be equal to` SoknadstypeDTO.FISKERE
        soknad.status `should be equal to` SoknadsstatusDTO.NY
        soknad.arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.FISKER
        soknad.fiskerLottOgHyre `should be equal to` FiskerLottOgHyreDTO.LOTT
        soknad.fiskerBlad `should be equal to` FiskerBladDTO.A
        soknad.arbeidsgiver.shouldBeNull()
        soknad.medlemskapVurdering.shouldBeNull()
    }

    @Test
    @Order(2)
    fun `Søknaden har næringsdrivendespørsmål`() {
        val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN_V2,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                FISKER_LOTT_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER,
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
    fun `Mottaker av søknaden er kun Nav`() {
        val soknadId = hentSoknaderMetadata(fnr).single().id
        finnMottakerAvSoknad(soknadId, fnr).mottaker `should be equal to` RSMottaker.NAV
    }

    @Test
    @Order(4)
    fun `Søknaden sendes kun til Nav`() {
        val soknad = hentSoknad(soknadId = hentSoknaderMetadata(fnr).single().id, fnr = fnr)

        val sendtSoknad =
            SoknadBesvarer(soknad, this, fnr)
                .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
                .besvarSporsmal(FRAVAR_FOR_SYKMELDINGEN_V2, "NEI")
                .besvarSporsmal(TILBAKE_I_ARBEID, "NEI")
                .besvarSporsmal("ARBEID_UNDERVEIS_100_PROSENT_0", "NEI")
                .besvarSporsmal(FISKER_LOTT_OPPRETTHOLDT_INNTEKT, "NEI")
                .besvarSporsmal(ANDRE_INNTEKTSKILDER, "NEI")
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
        kafkaSoknad.fiskerLottOgHyre `should be equal to` FiskerLottOgHyreDTO.LOTT
        kafkaSoknad.arbeidsgiver.shouldBeNull()
        kafkaSoknad.sendtNav.shouldNotBeNull()
        kafkaSoknad.sendtArbeidsgiver.shouldBeNull()
        kafkaSoknad.medlemskapVurdering.shouldBeNull()

        hentJuridiskeVurderinger(antall = 1).single().paragraf `should be equal to` "8-13"
    }

    @Test
    @Order(5)
    fun `Søknad nummer to i samme sykeforlop får ikke førstegangsspørsmål for næringsdrivende`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingId = andreSykmeldingId,
                arbeidssituasjon = Arbeidssituasjon.FISKER,
                lottOgHyre = LottOgHyre.LOTT,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2020, 2, 16),
                        tom = LocalDate.of(2020, 2, 29),
                    ),
            ),
            oppfolgingsdato = LocalDate.of(2020, 2, 1),
            sykmeldingIderMedSammeVentetid = setOf(forsteSykmeldingId),
        ).single()

        val andreSoknad = hentSoknaderMetadata(fnr).single { it.status == RSSoknadstatus.NY }
        val soknad = hentSoknad(soknadId = andreSoknad.id, fnr = fnr)

        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                FISKER_LOTT_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }
}
