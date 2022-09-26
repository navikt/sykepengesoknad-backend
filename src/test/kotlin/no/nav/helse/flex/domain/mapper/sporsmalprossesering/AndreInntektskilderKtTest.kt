package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.mapper.ArbeidsledigsoknadToSykepengesoknadDTO
import no.nav.helse.flex.mock.opprettSendtSoknad
import no.nav.helse.flex.mock.opprettSendtSoknadForArbeidsledige
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ER_DU_SYKMELDT
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_ANNET
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSKILDE_FRILANSER
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.testutil.besvarsporsmal
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class AndreInntektskilderKtTest {

    @Test
    fun `Arbeidsledig henter andre inntektskilder`() {
        val soknad = opprettSendtSoknadForArbeidsledige()
            .besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "JA")
            .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD, svar = "CHECKED")
            .besvarsporsmal(tag = INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD + ER_DU_SYKMELDT, svar = "JA")
            .besvarsporsmal(tag = INNTEKTSKILDE_FRILANSER, svar = "CHECKED")
            .besvarsporsmal(tag = INNTEKTSKILDE_FRILANSER + ER_DU_SYKMELDT, svar = "NEI")
            .besvarsporsmal(tag = INNTEKTSKILDE_ANNET, svar = "CHECKED")

        val andreInntektskilder = ArbeidsledigsoknadToSykepengesoknadDTO.konverterArbeidsledigTilSykepengesoknadDTO(
            soknad
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

        val andreInntektskilder = ArbeidsledigsoknadToSykepengesoknadDTO.konverterArbeidsledigTilSykepengesoknadDTO(
            besvartSoknad
        ).andreInntektskilder!!

        andreInntektskilder.shouldHaveSize(3)

        andreInntektskilder[0].type.shouldBeEqualTo(InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD)
        andreInntektskilder[0].sykmeldt.shouldBeEqualTo(true)

        andreInntektskilder[1].type.shouldBeEqualTo(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE)
        andreInntektskilder[1].sykmeldt.shouldBeEqualTo(true)

        andreInntektskilder[2].type.shouldBeEqualTo(InntektskildetypeDTO.SELVSTENDIG_NARINGSDRIVENDE_DAGMAMMA)
        andreInntektskilder[2].sykmeldt.shouldBeEqualTo(false)
    }
}
