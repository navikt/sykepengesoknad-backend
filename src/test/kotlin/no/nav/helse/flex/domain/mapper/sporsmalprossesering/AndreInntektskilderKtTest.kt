package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.mapper.konverterTilSykepengesoknadDTO
import no.nav.helse.flex.mock.opprettBehandlingsdagsoknadTestadata
import no.nav.helse.flex.mock.opprettNySoknadAnnet
import no.nav.helse.flex.mock.opprettSendtFrilanserSoknad
import no.nav.helse.flex.mock.opprettSendtSoknad
import no.nav.helse.flex.mock.opprettSendtSoknadForArbeidsledige
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class AndreInntektskilderKtTest {
    @Test
    fun `Arbeidsledig har ikke svart på andre inntektskilder`() {
        val soknad =
            opprettSendtSoknadForArbeidsledige()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(0)
    }

    @Test
    fun `Arbeidsledig henter andre inntektskilder`() {
        val soknad =
            opprettSendtSoknadForArbeidsledige()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD + ER_DU_SYKMELDT, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_FRILANSER, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_FRILANSER + ER_DU_SYKMELDT, svar = "NEI")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANNET, svar = "CHECKED")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(3)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeEqualTo(true)

        andreInntektskilder[1].type.shouldBeEqualTo(InntektskildetypeDTO.FRILANSER)
        andreInntektskilder[1].sykmeldt.shouldBeEqualTo(false)

        andreInntektskilder[2].type.shouldBeEqualTo(InntektskildetypeDTO.ANNET)
        andreInntektskilder[2].sykmeldt.shouldBeEqualTo(null)
    }

    @Test
    fun `Arbeidstakere henter andre inntektskilder`() {
        val besvartSoknad = opprettSendtSoknad()

        besvartSoknad
            .getSporsmalMedTag(INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD)
            .undersporsmal
            .map { it.tag }
            .shouldBeEqualTo(
                listOf(
                    INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE,
                ),
            )

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                besvartSoknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(besvartSoknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(3)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeNull()

        andreInntektskilder[1].type.shouldBeEqualTo(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE)
        andreInntektskilder[1].sykmeldt.shouldBeNull()

        andreInntektskilder[2].type.shouldBeEqualTo(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA)
        andreInntektskilder[2].sykmeldt.shouldBeNull()
    }

    @Test
    fun `Frilanser henter andre inntektskilder`() {
        val besvartSoknad = opprettSendtFrilanserSoknad()

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                besvartSoknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(besvartSoknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(4)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeEqualTo(false)

        andreInntektskilder[1].type.shouldBeEqualTo(InntektskildetypeDTO.JORDBRUKER_FISKER_REINDRIFTSUTOVER)
        andreInntektskilder[1].sykmeldt.shouldBeEqualTo(true)

        andreInntektskilder[2].type.shouldBeEqualTo(InntektskildetypeDTO.FRILANSER_SELVSTENDIG)
        andreInntektskilder[2].sykmeldt.shouldBeEqualTo(true)

        andreInntektskilder[3].type.shouldBeEqualTo(InntektskildetypeDTO.ANNET)
        andreInntektskilder[3].sykmeldt.shouldBeEqualTo(null)
    }

    @Test
    fun `Behandlingsdager henter andre inntektskilder`() {
        val soknad =
            opprettBehandlingsdagsoknadTestadata()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD + ER_DU_SYKMELDT, svar = "JA")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(1)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeEqualTo(true)
    }

    @Test
    fun `Annet henter andre inntektskilder`() {
        val soknad =
            opprettNySoknadAnnet()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD + ER_DU_SYKMELDT, svar = "JA")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(1)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeEqualTo(true)
    }
}
