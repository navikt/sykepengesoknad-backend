package no.nav.helse.flex.frisktilarbeid

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

class DefaultPeriodeGeneratorTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("vedtaksperioder")
    fun `Deler vedtaksperioden i sammenhengende perioder på inntil 14 dager`(
        beskrivelse: String,
        fom: String,
        tom: String,
        forventedePerioder: List<Pair<LocalDate, LocalDate>>,
    ) {
        withClue(beskrivelse) {
            defaultPeriodeGenerator(
                periodeStart = LocalDate.parse(fom),
                periodeSlutt = LocalDate.parse(tom),
            ) shouldBe forventedePerioder
        }
    }

    @Test
    fun `Bruker angitt periodelengde og forkorter siste periode`() {
        defaultPeriodeGenerator(
            periodeStart = LocalDate.of(2025, 1, 1),
            periodeSlutt = LocalDate.of(2025, 1, 31),
            periodeLengde = 7,
        ) shouldBe
            listOf(
                periode("2025-01-01", "2025-01-07"),
                periode("2025-01-08", "2025-01-14"),
                periode("2025-01-15", "2025-01-21"),
                periode("2025-01-22", "2025-01-28"),
                periode("2025-01-29", "2025-01-31"),
            )
    }

    @Test
    fun `Avviser sluttdato før startdato`() {
        assertThrows<IllegalArgumentException> {
            defaultPeriodeGenerator(
                periodeStart = LocalDate.of(2025, 2, 1),
                periodeSlutt = LocalDate.of(2025, 1, 1),
            )
        }
    }

    companion object {
        @JvmStatic
        fun vedtaksperioder(): List<Arguments> =
            listOf(
                Arguments.of(
                    "Én dag",
                    "2025-01-01",
                    "2025-01-01",
                    listOf(periode("2025-01-01", "2025-01-01")),
                ),
                Arguments.of(
                    "13 dager gir én forkortet periode",
                    "2025-04-07",
                    "2025-04-19",
                    listOf(periode("2025-04-07", "2025-04-19")),
                ),
                Arguments.of(
                    "14 dager gir én full periode",
                    "2025-04-07",
                    "2025-04-20",
                    listOf(periode("2025-04-07", "2025-04-20")),
                ),
                Arguments.of(
                    "15 dager gir en full periode og én dag",
                    "2025-04-07",
                    "2025-04-21",
                    listOf(
                        periode("2025-04-07", "2025-04-20"),
                        periode("2025-04-21", "2025-04-21"),
                    ),
                ),
                Arguments.of(
                    "27 dager gir 14 og 13 dager",
                    "2025-04-07",
                    "2025-05-03",
                    listOf(
                        periode("2025-04-07", "2025-04-20"),
                        periode("2025-04-21", "2025-05-03"),
                    ),
                ),
                Arguments.of(
                    "28 dager gir to fulle perioder",
                    "2025-04-07",
                    "2025-05-04",
                    listOf(
                        periode("2025-04-07", "2025-04-20"),
                        periode("2025-04-21", "2025-05-04"),
                    ),
                ),
                Arguments.of(
                    "29 dager gir to fulle perioder og én dag",
                    "2025-04-07",
                    "2025-05-05",
                    listOf(
                        periode("2025-04-07", "2025-04-20"),
                        periode("2025-04-21", "2025-05-04"),
                        periode("2025-05-05", "2025-05-05"),
                    ),
                ),
                Arguments.of(
                    "30 dager gir to fulle perioder og to dager",
                    "2025-04-07",
                    "2025-05-06",
                    listOf(
                        periode("2025-04-07", "2025-04-20"),
                        periode("2025-04-21", "2025-05-04"),
                        periode("2025-05-05", "2025-05-06"),
                    ),
                ),
                Arguments.of(
                    "En hel måned gir to fulle perioder og tre dager",
                    "2025-01-01",
                    "2025-01-31",
                    listOf(
                        periode("2025-01-01", "2025-01-14"),
                        periode("2025-01-15", "2025-01-28"),
                        periode("2025-01-29", "2025-01-31"),
                    ),
                ),
                Arguments.of(
                    "Onsdag til onsdag over tolv uker",
                    "2025-03-26",
                    "2025-06-18",
                    listOf(
                        periode("2025-03-26", "2025-04-08"),
                        periode("2025-04-09", "2025-04-22"),
                        periode("2025-04-23", "2025-05-06"),
                        periode("2025-05-07", "2025-05-20"),
                        periode("2025-05-21", "2025-06-03"),
                        periode("2025-06-04", "2025-06-17"),
                        periode("2025-06-18", "2025-06-18"),
                    ),
                ),
                Arguments.of(
                    "Skuddagen inngår i perioden",
                    "2024-02-20",
                    "2024-03-05",
                    listOf(
                        periode("2024-02-20", "2024-03-04"),
                        periode("2024-03-05", "2024-03-05"),
                    ),
                ),
                Arguments.of(
                    "Årsskifte deler ikke en full periode",
                    "2025-12-25",
                    "2026-01-08",
                    listOf(
                        periode("2025-12-25", "2026-01-07"),
                        periode("2026-01-08", "2026-01-08"),
                    ),
                ),
            )

        private fun periode(
            fom: String,
            tom: String,
        ): Pair<LocalDate, LocalDate> = LocalDate.parse(fom) to LocalDate.parse(tom)
    }
}
