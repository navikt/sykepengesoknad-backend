package no.nav.helse.flex.util

import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SvartypeDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SporsmalDTOUtilTest {

    @Test
    fun `flatten finner alle undersporsmal rekursivt`() {
        val hoved = SporsmalDTO(
            id = "1",
            tag = "HOVED",
            sporsmalstekst = "Hoved",
            undertekst = null,
            min = null,
            max = null,
            svartype = SvartypeDTO.JA_NEI,
            kriterieForVisningAvUndersporsmal = null,
            svar = emptyList(),
            undersporsmal = listOf(
                        SporsmalDTO(
                            id = "2",
                            tag = "UNDERSPORSMAL",
                            sporsmalstekst = "Under",
                            undertekst = null,
                            min = null,
                            max = null,
                            svartype = SvartypeDTO.JA_NEI,
                            kriterieForVisningAvUndersporsmal = null,
                            svar = emptyList(),
                            undersporsmal = emptyList(),
                        ),
                    ),
            )

        assertThat(listOf(hoved).flatten().map { it.tag }).containsExactly("HOVED", "UNDERSPORSMAL")
    }

    @Test
    fun `getSporsmalMedTag finner sporsmal ved tag`() {
        val sporsmal = listOf(
            SporsmalDTO(
                id = "1",
                tag = "HOVED",
                sporsmalstekst = "Hoved",
                undertekst = null,
                min = null,
                max = null,
                svartype = SvartypeDTO.JA_NEI,
                kriterieForVisningAvUndersporsmal = null,
                svar = emptyList(),
                undersporsmal = listOf(
                            SporsmalDTO(
                                id = "2",
                                tag = "MAL",
                                sporsmalstekst = "Mål",
                                undertekst = null,
                                min = null,
                                max = null,
                                svartype = SvartypeDTO.JA_NEI,
                                kriterieForVisningAvUndersporsmal = null,
                                svar = emptyList(),
                                undersporsmal = emptyList(),
                            ),
                        ),
                ),
            )

        assertThat(sporsmal.getSporsmalMedTag("MAL").id).isEqualTo("2")
        assertThat(sporsmal.getSporsmalMedTagOrNull("MAL")?.id).isEqualTo("2")
    }

    @Test
    fun `getSporsmalMedTag kaster hvis tag ikke finnes`() {
        val sporsmal = emptyList<SporsmalDTO>()

        assertThrows<RuntimeException> {
            sporsmal.getSporsmalMedTag("UKJENT")
        }
    }
}
