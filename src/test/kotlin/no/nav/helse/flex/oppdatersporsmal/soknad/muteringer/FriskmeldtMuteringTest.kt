package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.mock.opprettNySoknadAnnet
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.testutil.besvarsporsmal
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class FriskmeldtMuteringTest {
    private val ghostInntekter =
        listOf(
            KjentInntektskilde(
                navn = "Frilanseransetter AS",
                kilde = Kilde.INNTEKTSKOMPONENTEN,
                orgnummer = "999333667",
            ),
        )

    private fun soknader(): List<Sykepengesoknad> {
        val arbeidsledig = opprettNySoknad()
        val annetArbeidsforhold = opprettNySoknadAnnet()
        return listOf(
            arbeidsledig,
            annetArbeidsforhold,
            arbeidsledig.copy(soknadstype = Soknadstype.GRADERT_REISETILSKUDD),
            annetArbeidsforhold.copy(soknadstype = Soknadstype.GRADERT_REISETILSKUDD),
        )
    }

    @TestFactory
    fun `V2 på toppnivå erstattes av en gruppe når søknaden har ghost-inntekter`() =
        soknader().map { soknad ->
            dynamicTest("${soknad.soknadstype} ${soknad.arbeidssituasjon}") {
                val gammelSoknad = soknad.copy(ghostInntekter = ghostInntekter)
                gammelSoknad.sporsmal.single { it.tag == ANDRE_INNTEKTSKILDER_V2 }

                val mutert = gammelSoknad.friskmeldtMuteringer()

                mutert.`har kun ett V2 som underspørsmål i gruppen`()
                mutert.friskmeldtMuteringer() shouldBe mutert
            }
        }

    @TestFactory
    fun `Eksisterende underspørsmål og svar beholdes ved gjentatt mutering`() =
        soknader().map { soknad ->
            dynamicTest("${soknad.soknadstype} ${soknad.arbeidssituasjon}") {
                val besvart =
                    soknad
                        .copy(ghostInntekter = ghostInntekter)
                        .friskmeldtMuteringer()
                        .besvarsporsmal(FLERE_INNTEKTSKILDER_GHOST, "JA")
                        .besvarsporsmal(JOBBET_MER_I_VALG + "0", "CHECKED")
                        .besvarsporsmal(ANDRE_INNTEKTSKILDER_V2, "JA")
                        .besvarsporsmal(INNTEKTSKILDE_STYREVERV, "CHECKED")

                val mutert = besvart.friskmeldtMuteringer()

                mutert.`har kun ett V2 som underspørsmål i gruppen`()
                mutert.getSporsmalMedTag(FLERE_INNTEKTSKILDER_GRUPPE) shouldBe
                    besvart.getSporsmalMedTag(FLERE_INNTEKTSKILDER_GRUPPE)
                mutert.friskmeldtMuteringer() shouldBe mutert
            }
        }

    @TestFactory
    fun `V2 og svar beholdes på toppnivå uten ghost-inntekter`() =
        soknader().flatMap { soknad ->
            listOf(null, emptyList<KjentInntektskilde>()).map { ingenGhostInntekter ->
                dynamicTest("${soknad.soknadstype} ${soknad.arbeidssituasjon} ghostInntekter=$ingenGhostInntekter") {
                    val besvart =
                        soknad
                            .copy(ghostInntekter = ingenGhostInntekter)
                            .besvarsporsmal(ANDRE_INNTEKTSKILDER_V2, "JA")
                            .besvarsporsmal(INNTEKTSKILDE_STYREVERV, "CHECKED")

                    val mutert = besvart.friskmeldtMuteringer()

                    mutert.sporsmal.single { it.tag == ANDRE_INNTEKTSKILDER_V2 } shouldBe
                        besvart.getSporsmalMedTag(ANDRE_INNTEKTSKILDER_V2)
                    mutert.sporsmal.none { it.tag == FLERE_INNTEKTSKILDER_GRUPPE } shouldBe true
                    mutert.alleSporsmalOgUndersporsmal().filter { it.tag == ANDRE_INNTEKTSKILDER_V2 }.shouldHaveSize(1)
                    mutert.friskmeldtMuteringer() shouldBe mutert
                }
            }
        }

    private fun Sykepengesoknad.`har kun ett V2 som underspørsmål i gruppen`() {
        sporsmal.none { it.tag == ANDRE_INNTEKTSKILDER_V2 } shouldBe true
        val gruppe = sporsmal.single { it.tag == FLERE_INNTEKTSKILDER_GRUPPE }
        gruppe.undersporsmal.map { it.tag } shouldBe
            listOf(FLERE_INNTEKTSKILDER_GHOST, ANDRE_INNTEKTSKILDER_V2)
        alleSporsmalOgUndersporsmal().filter { it.tag == ANDRE_INNTEKTSKILDER_V2 }.shouldHaveSize(1)
    }
}
