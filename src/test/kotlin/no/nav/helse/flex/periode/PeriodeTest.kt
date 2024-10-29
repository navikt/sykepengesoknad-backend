package no.nav.helse.flex.periode

import no.nav.helse.flex.domain.Periode
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate.of

class PeriodeTest {
    @Test
    fun `skal returnere 5 ukedager for en periode fra mandag til søndag`() {
        val periode =
            Periode(
                of(2024, 10, 21),
                of(2024, 10, 27),
            ) // Mandag til søndag

        periode.hentUkedager().size `should be equal to` 5 // Forventer kun ukedager, dvs. mandag til fredag
    }

    @Test
    fun `skal returnere 1 ukedag når start og slutt er samme ukedag`() {
        val sammeDag = of(2024, 10, 23) // Onsdag
        val periodeSammeDag = Periode(sammeDag, sammeDag)

        periodeSammeDag.hentUkedager().size `should be equal to` 1 // Forventer kun én ukedag
    }

    @Test
    fun `skal returnere 2 ukedager for en periode fra fredag til mandag inkludert helg`() {
        val fredagTilMandag =
            Periode(
                of(2024, 10, 25),
                of(2024, 10, 28),
            ) // Fredag til mandag

        fredagTilMandag.hentUkedager().size `should be equal to` 2 // Forventer fredag og mandag, ingen helgedager
    }

    @Test
    fun `skal returnere 0 ukedager for en periode som kun dekker helgedager`() {
        val kunHelg =
            Periode(
                of(2024, 10, 26),
                of(2024, 10, 27),
            ) // Lørdag til søndag

        kunHelg.hentUkedager().size `should be equal to` 0 // Forventer ingen ukedager
    }

    @Test
    fun `skal returnere riktig antall ukedager for en periode som går over flere uker`() {
        val overFlereUker =
            Periode(
                of(2024, 10, 18),
                of(2024, 10, 31),
            ) // Fredag til neste torsdag

        overFlereUker.hentUkedager().size `should be equal to` 10 // Forventer 10 ukedager (fre, man-fre, man-tor)
    }

    @Test
    fun testerOverlapp() {
        val periode1 = Periode(of(2024, 10, 21), of(2024, 10, 27))
        val periode2 = Periode(of(2024, 10, 24), of(2024, 10, 30))

        periode1.overlapper(periode2) `should be equal to` true
    }
}
