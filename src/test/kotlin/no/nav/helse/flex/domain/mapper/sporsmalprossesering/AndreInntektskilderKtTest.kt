package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.mapper.konverterTilSykepengesoknadDTO
import no.nav.helse.flex.mock.opprettBehandlingsdagsoknadTestadata
import no.nav.helse.flex.mock.opprettNyArbeidstakerSoknad
import no.nav.helse.flex.mock.opprettNySoknadAnnet
import no.nav.helse.flex.mock.opprettSendtFrilanserSoknad
import no.nav.helse.flex.mock.opprettSendtSoknadForArbeidsledige
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.muterErstattGammelAndreInntektskilder
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import org.junit.jupiter.api.Test

class AndreInntektskilderKtTest : FellesTestOppsett() {
    @Test
    fun `Arbeidsledig har ikke svart på andre inntektskilder`() {
        val soknad =
            opprettSendtSoknadForArbeidsledige()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")

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
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_FRILANSER, svar = "CHECKED")
                .besvarsporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(3)

        andreInntektskilder[0].type.shouldBe(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)

        andreInntektskilder[1].type.shouldBe(InntektskildetypeDTO.FRILANSER)

        andreInntektskilder[2].type.shouldBe(InntektskildetypeDTO.STYREVERV)
    }

    @Test
    fun `Arbeidstakere henter andre inntektskilder`() {
        val besvartSoknad = opprettNyArbeidstakerSoknad()

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                besvartSoknad,
                Mottaker.ARBEIDSGIVER_OG_NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(besvartSoknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(3)

        andreInntektskilder[0].type.shouldBe(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeNull()

        andreInntektskilder[1].type.shouldBe(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE)
        andreInntektskilder[1].sykmeldt.shouldBeNull()

        andreInntektskilder[2].type.shouldBe(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA)
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

        andreInntektskilder[0].type.shouldBe(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[1].type.shouldBe(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA)
        andreInntektskilder[2].type.shouldBe(InntektskildetypeDTO.FOSTERHJEMGODTGJORELSE)
        andreInntektskilder[3].type.shouldBe(InntektskildetypeDTO.STYREVERV)
    }

    @Test
    fun `Behandlingsdager henter ANDRE_INNTEKTSKILDER_V2`() {
        val soknad =
            opprettBehandlingsdagsoknadTestadata()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(1)

        andreInntektskilder[0].type.shouldBe(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
    }

    @Test
    fun `Behandlingsdager muterer bort gammel ANDRE_INNTEKTSKILDER`() {
        val soknad =
            opprettBehandlingsdagsoknadTestadata()
                .fjernSporsmal(ANDRE_INNTEKTSKILDER_V2)
                .leggTilSporsmaal(Sporsmal(tag = ANDRE_INNTEKTSKILDER, svartype = Svartype.JA_NEI))

        soknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER).shouldNotBeNull()
        soknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER_V2).shouldBeNull()

        val mutertSoknad = soknad.muterErstattGammelAndreInntektskilder()

        mutertSoknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER).shouldBeNull()
        mutertSoknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER_V2).shouldNotBeNull()
    }

    @Test
    fun `Annet henter andre inntektskilder`() {
        val soknad =
            opprettNySoknadAnnet()
                .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA")
                .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")

        val andreInntektskilder =
            konverterTilSykepengesoknadDTO(
                soknad,
                Mottaker.NAV,
                false,
                hentSoknadsPerioderMedFaktiskGrad(soknad).first,
            ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(1)

        andreInntektskilder[0].type.shouldBe(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
    }
}
