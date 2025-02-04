package no.nav.helse.flex.frisktilarbeid

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class FriskTilArbeidSoknadServiceTest {
    @Test
    fun `Generer perioder for en måned med default periodelengde`() {
        defaultSoknadPeriodeGenerator(
            fom = LocalDate.of(2025, 1, 1),
            tom = LocalDate.of(2025, 1, 31),
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
    fun `Generer perioder for en måned med angitt periodelengde`() {
        defaultSoknadPeriodeGenerator(
            fom = LocalDate.of(2025, 1, 1),
            tom = LocalDate.of(2025, 1, 31),
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
    fun `Generer perioder for en enkelt dag`() {
        defaultSoknadPeriodeGenerator(
            fom = LocalDate.of(2025, 1, 1),
            tom = LocalDate.of(2025, 1, 1),
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
            defaultSoknadPeriodeGenerator(
                fom = LocalDate.of(2025, 2, 1),
                tom = LocalDate.of(2025, 1, 1),
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
