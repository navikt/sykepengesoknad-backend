package no.nav.helse.flex.util

import no.nav.helse.flex.util.PeriodeMapper.jsonISOFormatTilPeriode
import no.nav.helse.flex.util.PeriodeMapper.jsonSporsmalstektsFormatTilPeriode
import no.nav.helse.flex.util.PeriodeMapper.jsonTilOptionalPeriode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class PeriodeMapperTest {
    @Test
    fun jsonMappesTilPeriode() {
        val (fom, tom) = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-01\",\"tom\":\"2018-02-01\"}")
        println(OffsetDateTime.now())
        assertThat(fom).isEqualTo("2018-01-01")
        assertThat(tom).isEqualTo("2018-02-01")
    }

    @Test
    fun mappingFeilerOmFomErEtterTom() {
        assertThrows(IllegalArgumentException::class.java) {
            jsonISOFormatTilPeriode("{\"fom\":\"2018-02-01\",\"tom\":\"2018-01-01\"}")
        }
    }

    @Test
    fun mappingFeilerOmFomErPaFeilFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            jsonISOFormatTilPeriode("{\"fom\":\"2018-02-01\",\"tom\":\"2018-01\"}")
        }
    }

    @Test
    fun mappingFeilerOmTomErPaFeilFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            jsonISOFormatTilPeriode("{\"fom\":\"2018-02\",\"tom\":\"2018-01-01\"}")
        }
    }

    @Test
    fun mappingFeilerOmFomErNull() {
        assertThrows(IllegalArgumentException::class.java) {
            jsonISOFormatTilPeriode("{\"fom\":null,\"tom\":\"2018-01-01\"}")
        }
    }

    @Test
    fun mappingFeilerOmTomErNull() {
        assertThrows(IllegalArgumentException::class.java) {
            jsonISOFormatTilPeriode("{\"fom\":\"2018-02-01\",\"tom\":null}")
        }
    }

    @Test
    fun erIPeriode_dagInnenforPeriodeGirTrue() {
        val periode = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}")

        val actual = periode.erIPeriode(LocalDate.of(2018, 1, 10))

        assertThat(actual).isTrue()
    }

    @Test
    fun erIPeriode_sammeDagSomFomGirTrue() {
        val periode = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}")

        val actual = periode.erIPeriode(LocalDate.of(2018, 1, 3))

        assertThat(actual).isTrue()
    }

    @Test
    fun erIPeriode_dagenForFomGirFalse() {
        val periode = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}")

        val actual = periode.erIPeriode(LocalDate.of(2018, 1, 2))

        assertThat(actual).isFalse()
    }

    @Test
    fun erIPeriode_sammeDagSomTomGirTrue() {
        val periode = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}")

        val actual = periode.erIPeriode(LocalDate.of(2018, 1, 20))

        assertThat(actual).isTrue()
    }

    @Test
    fun erIPeriode_dagenEtterTomGirFalse() {
        val periode = jsonISOFormatTilPeriode("{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}")

        val actual = periode.erIPeriode(LocalDate.of(2018, 1, 21))

        assertThat(actual).isFalse()
    }

    @Test
    fun jsonMedISOFormatMappesTilPeriode() {
        val gyldigJson = "{\"fom\":\"2018-01-03\",\"tom\":\"2018-01-20\"}"

        val optionalPeriode = jsonTilOptionalPeriode(gyldigJson)

        assertThat(optionalPeriode).isPresent()
    }

    @Test
    fun jsonMedSporsmalstekstFormatMappesTilPeriode() {
        val gyldigJson = "{\"fom\":\"01.02.2018\",\"tom\":\"07.02.2018\"}"

        val (fom, tom) = jsonSporsmalstektsFormatTilPeriode(gyldigJson)

        assertThat(fom).isEqualTo(LocalDate.of(2018, 2, 1))
        assertThat(tom).isEqualTo(LocalDate.of(2018, 2, 7))
    }

    @Test
    fun ugyldigJsonMappesTilEmpty() {
        val ugyldigJson = "{\"fom\":\"2018-01-03\",\"tom\":\"20__-01-20\"}"

        val optionalPeriode = jsonTilOptionalPeriode(ugyldigJson)

        assertThat(optionalPeriode).isNotPresent()
    }
}
