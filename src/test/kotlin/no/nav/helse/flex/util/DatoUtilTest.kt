package no.nav.helse.flex.util

import no.nav.helse.flex.util.PeriodeMapper.jsonISOFormatTilPeriode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate.now

class DatoUtilTest {
    @Test
    fun mapperPeriodeTilJsonstreng() {
        val idag = now()
        val imorgen = idag.plusDays(1)

        val json = periodeTilJson(idag, imorgen)
        val (fom, tom) = jsonISOFormatTilPeriode(json)

        assertThat(fom).isEqualTo(idag)
        assertThat(tom).isEqualTo(imorgen)
    }
}
