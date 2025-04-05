package no.nav.helse.flex.frisktilarbeid

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class FriskTilArbeidSoknadServiceTest {
    @Test
    fun `Generer to perioder på 14 dager for vedtaksperiode på 28 dager`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 4, 7),
            periodeSlutt = LocalDate.of(2025, 5, 4),
        ).also { perioder ->
            assertEquals(2, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-04-07", "2025-04-20", 14L),
                    ForventetPeriode("2025-04-21", "2025-05-04", 14L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer to perioder på 14 dager og 13 dager for vedtaksperiode på 27 dager`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 4, 7),
            periodeSlutt = LocalDate.of(2025, 5, 3),
        ).also { perioder ->
            assertEquals(2, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-04-07", "2025-04-20", 14L),
                    ForventetPeriode("2025-04-21", "2025-05-03", 13L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer 2 periode på 14 dager og én periode på 1 dag for vedtaksperiode på 29 dager`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 4, 7),
            periodeSlutt = LocalDate.of(2025, 5, 5),
        ).also { perioder ->
            assertEquals(3, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-04-07", "2025-04-20", 14L),
                    ForventetPeriode("2025-04-21", "2025-05-04", 14L),
                    ForventetPeriode("2025-05-05", "2025-05-05", 1L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer 2 periode på 14 dager og én periode på 2 dager for vedtaksperiode på 30 dager`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 4, 7),
            periodeSlutt = LocalDate.of(2025, 5, 6),
        ).also { perioder ->
            assertEquals(3, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-04-07", "2025-04-20", 14L),
                    ForventetPeriode("2025-04-21", "2025-05-04", 14L),
                    ForventetPeriode("2025-05-05", "2025-05-06", 2L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer perioder for en vedtaksperiode på en måned med default periodelengde`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 1, 1),
            periodeSlutt = LocalDate.of(2025, 1, 31),
        ).also { perioder ->
            assertEquals(3, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-01-01", "2025-01-14", 14L),
                    ForventetPeriode("2025-01-15", "2025-01-28", 14L),
                    ForventetPeriode("2025-01-29", "2025-01-31", 3L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer perioder for vedtaksperiode som går fra onsdag til onsdag`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 3, 26),
            periodeSlutt = LocalDate.of(2025, 6, 18),
        ).also { perioder ->
            assertEquals(7, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-03-26", "2025-04-08", 14L),
                    ForventetPeriode("2025-04-09", "2025-04-22", 14L),
                    ForventetPeriode("2025-04-23", "2025-05-06", 14L),
                    ForventetPeriode("2025-05-07", "2025-05-20", 14L),
                    ForventetPeriode("2025-05-21", "2025-06-03", 14L),
                    ForventetPeriode("2025-06-04", "2025-06-17", 14L),
                    // Perioden er 12 uker + 1 dag lang.
                    ForventetPeriode("2025-06-18", "2025-06-18", 1L),
                )
            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generer perioder for en vedtaksperiode på en måned med angitt periodelengde`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 1, 1),
            periodeSlutt = LocalDate.of(2025, 1, 31),
            periodeLengde = 7,
        ).also {
            assertEquals(5, it.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-01-01", "2025-01-07", 7L),
                    ForventetPeriode("2025-01-08", "2025-01-14", 7L),
                    ForventetPeriode("2025-01-15", "2025-01-21", 7L),
                    ForventetPeriode("2025-01-22", "2025-01-28", 7L),
                    ForventetPeriode("2025-01-29", "2025-01-31", 3L),
                )
            sammenlignPerioder(forventedePerioder = forventedePerioder, generertePerioder = it)
        }
    }

    @Test
    fun `Generer periode for en vedtaksperiode på én dag dag`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 1, 1),
            periodeSlutt = LocalDate.of(2025, 1, 1),
        ).also { perioder ->
            assertEquals(1, perioder.size)

            val forventedePerioder =
                listOf(
                    ForventetPeriode("2025-01-01", "2025-01-01", 1L),
                )

            sammenlignPerioder(forventedePerioder, perioder)
        }
    }

    @Test
    fun `Generering av perioder feiler når tom er før tom`() {
        assertThrows<IllegalArgumentException> {
            defaultPeriodeGenerator(
                periodeStart = LocalDate.of(2025, 2, 1),
                periodeSlutt = LocalDate.of(2025, 1, 1),
            )
        }
    }

    private fun sammenlignPerioder(
        forventedePerioder: List<ForventetPeriode>,
        generertePerioder: List<Pair<LocalDate, LocalDate>>,
    ) {
        forventedePerioder.forEachIndexed { i, forventetPeriode ->
            tellAntallDager(
                generertePerioder[i].first,
                generertePerioder[i].second,
            ) `should be equal to` forventetPeriode.antallDager
            generertePerioder[i].first `should be equal to` forventetPeriode.fom
            generertePerioder[i].second `should be equal to` forventetPeriode.tom
        }
    }

    private fun tellAntallDager(
        fom: LocalDate,
        tom: LocalDate,
    ): Long {
        return ChronoUnit.DAYS.between(fom, tom) + 1
    }

    private data class ForventetPeriode(val fomString: String, val tomString: String, val antallDager: Long) {
        val fom: LocalDate = LocalDate.parse(fomString)
        val tom: LocalDate = LocalDate.parse(tomString)
    }
}
