package no.nav.helse.flex.domain

import no.nav.helse.flex.soknadsopprettelse.settOppSoknadOppholdUtland
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SykepengesoknadTest {
    private val sykepengesoknad: Sykepengesoknad
        get() = settOppSoknadOppholdUtland("fnr")

    @Test
    fun alleSporsmalOgUndersporsmalReturnererAlleSporsmal() {
        val sporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()

        assertThat(
            sporsmalOgUndersporsmal.map {
                it.sporsmalstekst
            }.map { i ->
                i!!.split(" ".toRegex()).dropLastWhile {
                    it.isEmpty()
                }.toTypedArray()[0]
            }.joinToString(","),
        ).isEqualTo("Når,Hvilke(t),Har,Er,Har,Viktig,Jeg")
    }

    @Test
    fun replaceSporsmalBytterUtSporsmal() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad =
            sykepengesoknad.replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER").copy(
                    sporsmalstekst = "HEISANN",
                ),
            )

        val sporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
        assertThat(
            sporsmalOgUndersporsmal.map { it.sporsmalstekst }.map { i ->
                i!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            }.joinToString(","),
        ).isEqualTo("Når,Hvilke(t),HEISANN,Er,Har,Viktig,Jeg")
    }

    @Test
    fun replaceSporsmalBytterIkkeAndreSporsmal() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad =
            sykepengesoknad.replaceSporsmal(
                sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER").copy(
                    sporsmalstekst = "9",
                ),
            )

        val sporsmal = sykepengesoknad.sporsmal
        assertThat(sporsmal).hasSize(4)
        assertThat(sporsmal[0].sporsmalstekst).isEqualTo("Når skal du reise?")
        assertThat(sporsmal[1].sporsmalstekst).isEqualTo("Hvilke(t) land skal du reise til?")
        assertThat(sporsmal[2].sporsmalstekst).isEqualTo("9")
        assertThat(sporsmal[3].sporsmalstekst).isEqualTo("Viktig å være klar over:")
    }

    @Test
    fun fjernHovedsporsmal() {
        var sykepengesoknad = sykepengesoknad
        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().size).isEqualTo(7)

        sykepengesoknad = sykepengesoknad.fjernSporsmal("ARBEIDSGIVER")

        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().size).isEqualTo(4)
        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().stream().anyMatch { (_, tag) -> tag == "1" }).isFalse()
    }

    @Test
    fun fjernUndersporsmal() {
        var sykepengesoknad = sykepengesoknad
        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().size).isEqualTo(7)

        sykepengesoknad = sykepengesoknad.fjernSporsmal("SYKMELDINGSGRAD")

        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().size).isEqualTo(6)
        assertThat(sykepengesoknad.alleSporsmalOgUndersporsmal().stream().anyMatch { (_, tag) -> tag == "2" }).isFalse()
    }

    @Test
    fun leggTilHovedsporsmalLeggerSegEttersporsmal() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad =
            sykepengesoknad.addHovedsporsmal(
                Sporsmal(
                    tag = "tag",
                    svartype = Svartype.CHECKBOX,
                ),
                sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER"),
            )

        assertThat(sykepengesoknad.sporsmal[3].tag).isEqualTo("tag")
        assertThat(sykepengesoknad.sporsmal.size).isEqualTo(5)
    }

    @Test
    fun leggTilHovedsporsmalUtenEttersporsmalLeggerSegSist() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad =
            sykepengesoknad.addHovedsporsmal(
                Sporsmal(
                    tag = "tag",
                    svartype = Svartype.CHECKBOX,
                ),
                null,
            )

        assertThat(sykepengesoknad.sporsmal[4].tag).isEqualTo("tag")
        assertThat(sykepengesoknad.sporsmal.size).isEqualTo(5)
    }
}
