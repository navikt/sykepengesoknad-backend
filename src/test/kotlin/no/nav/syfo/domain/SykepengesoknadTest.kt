package no.nav.syfo.domain

import no.nav.syfo.soknadsopprettelse.settOppSoknadOppholdUtland
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SykepengesoknadTest {

    private val sykepengesoknad: Sykepengesoknad
        get() = settOppSoknadOppholdUtland("fnr")

    @Test
    fun alleSporsmalOgUndersporsmalReturnererAlleSporsmal() {
        val sporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()

        assertThat(sporsmalOgUndersporsmal.map { it.sporsmalstekst }.map { i -> i!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] }.joinToString(",")).isEqualTo("Når,Hvilket,Har,Er,Har,Før,Jeg")
    }

    @Test
    fun replaceSporsmalBytterUtSporsmal() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER").toBuilder()
                .sporsmalstekst("HEISANN")
                .build()
        )

        val sporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
        assertThat(sporsmalOgUndersporsmal.map { it.sporsmalstekst }.map { i -> i!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] }.joinToString(",")).isEqualTo("Når,Hvilket,HEISANN,Er,Har,Før,Jeg")
    }

    @Test
    fun replaceSporsmalBytterIkkeAndreSporsmal() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad = sykepengesoknad.replaceSporsmal(
            sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER").toBuilder()
                .sporsmalstekst("9")
                .build()
        )

        val sporsmal = sykepengesoknad.sporsmal
        assertThat(sporsmal).hasSize(4)
        assertThat(sporsmal[0].sporsmalstekst).isEqualTo("Når skal du reise?")
        assertThat(sporsmal[1].sporsmalstekst).isEqualTo("Hvilket land skal du reise til?")
        assertThat(sporsmal[2].sporsmalstekst).isEqualTo("9")
        assertThat(sporsmal[3].sporsmalstekst).isEqualTo("Før du reiser ber vi deg bekrefte:")
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

        sykepengesoknad = sykepengesoknad.addHovedsporsmal(
            sporsmalBuilder()
                .tag("tag")
                .svartype(Svartype.CHECKBOX)
                .build(),
            sykepengesoknad.getSporsmalMedTag("ARBEIDSGIVER")
        )

        assertThat(sykepengesoknad.sporsmal[3].tag).isEqualTo("tag")
        assertThat(sykepengesoknad.sporsmal.size).isEqualTo(5)
    }

    @Test
    fun leggTilHovedsporsmalUtenEttersporsmalLeggerSegSist() {
        var sykepengesoknad = sykepengesoknad

        sykepengesoknad = sykepengesoknad.addHovedsporsmal(
            sporsmalBuilder()
                .tag("tag")
                .svartype(Svartype.CHECKBOX)
                .build(),
            null
        )

        assertThat(sykepengesoknad.sporsmal[4].tag).isEqualTo("tag")
        assertThat(sykepengesoknad.sporsmal.size).isEqualTo(5)
    }
}
