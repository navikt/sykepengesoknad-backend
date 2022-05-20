package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.util.DatoUtil.periodeErUtenforHelg
import no.nav.helse.flex.util.DatoUtil.periodeHarDagerUtenforAndrePerioder
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import no.nav.helse.flex.util.PeriodeMapper.jsonISOFormatTilPeriode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek.*
import java.time.LocalDate.now
import java.time.LocalDate.of
import java.time.temporal.TemporalAdjusters.next
import java.util.Arrays.asList

class DatoUtilTest {

    @Test
    fun sjekkAtPeriodeErIHelg() {
        val lordag = now().with(next(SATURDAY))
        val sondag = lordag.with(next(SUNDAY))

        val helgeperiode = Periode(lordag, sondag)

        assertThat(periodeErUtenforHelg(helgeperiode)).isFalse()
    }

    @Test
    fun sjekkAtPeriodeErUtenforHelg() {
        val lordag = now().with(next(SATURDAY))
        val mandag = lordag.with(next(MONDAY))

        val helgeperiode = Periode(lordag, mandag)

        assertThat(periodeErUtenforHelg(helgeperiode)).isTrue()
    }

    @Test
    fun mapperPeriodeTilJsonstreng() {
        val idag = now()
        val imorgen = idag.plusDays(1)

        val json = periodeTilJson(idag, imorgen)
        val (fom, tom) = jsonISOFormatTilPeriode(json)

        assertThat(fom).isEqualTo(idag)
        assertThat(tom).isEqualTo(imorgen)
    }

    @Test
    fun dagAvPeriodeErUtenforAndrePerioder() {
        val periode = Periode(of(2019, 2, 10), of(2019, 2, 17))

        val andrePerioder = asList(
            Periode(of(2019, 2, 5), of(2019, 2, 15)),
            Periode(of(2019, 2, 17), of(2019, 2, 19))
        )

        assertThat(periodeHarDagerUtenforAndrePerioder(periode, andrePerioder)).isTrue()
    }

    @Test
    fun helePeriodenErInnenforAndrePerioder() {
        val periode = Periode(of(2019, 2, 10), of(2019, 2, 17))

        val andrePerioder = asList(
            Periode(of(2019, 2, 5), of(2019, 2, 15)),
            Periode(of(2019, 2, 16), of(2019, 2, 19))
        )

        assertThat(periodeHarDagerUtenforAndrePerioder(periode, andrePerioder)).isFalse()
    }
}
