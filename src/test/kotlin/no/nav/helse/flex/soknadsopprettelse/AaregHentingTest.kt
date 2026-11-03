package no.nav.helse.flex.soknadsopprettelse

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.soknadsopprettelse.aaregdata.AaregDataHenting
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

private const val FNR_MED_2_ARBEIDSFORHOLD = "22222220001"

class AaregHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var aaregDataHenting: AaregDataHenting

    @Test
    fun `finner ingen nye arbeidsforhold`() {
        val soknad =
            opprettNySoknad().copy(
                arbeidsgiverOrgnummer = "999333666",
                startSykeforlop = LocalDate.of(2022, 9, 15),
                tom = LocalDate.of(2022, 9, 15),
            )
        aaregDataHenting
            .hentNyeArbeidsforhold(
                fnr = "11111234565",
                eksisterendeSoknader = emptyList(),
                sykepengesoknad = soknad,
            ).shouldBeEmpty()
    }

    @Test
    fun `finner to nye arbeidsforhold`() {
        val soknad =
            opprettNySoknad().copy(
                arbeidsgiverOrgnummer = "112233445",
                startSykeforlop = LocalDate.of(2022, 9, 15).minusDays(50),
                tom = LocalDate.of(2022, 9, 15),
                fom = LocalDate.of(2022, 9, 15).minusDays(50),
            )
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = FNR_MED_2_ARBEIDSFORHOLD,
                eksisterendeSoknader = emptyList(),
                sykepengesoknad = soknad,
            )
        nyeArbeidsforhold.shouldHaveSize(2)
        nyeArbeidsforhold[0].arbeidsstedNavn shouldBe "Bensinstasjonen AS"
        nyeArbeidsforhold[1].arbeidsstedNavn shouldBe "Kiosken, avd Oslo AS"
    }

    @Test
    fun `tar ikke med arbeidsforhold som er oppstått etter søknadens tom`() {
        val soknad =
            opprettNySoknad().copy(
                arbeidsgiverOrgnummer = "112233445",
                startSykeforlop = LocalDate.of(2022, 9, 15).minusDays(50),
                tom = LocalDate.of(2022, 9, 15).minusDays(42),
            )
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = FNR_MED_2_ARBEIDSFORHOLD,
                eksisterendeSoknader = emptyList(),
                sykepengesoknad = soknad,
            )
        nyeArbeidsforhold.shouldBeEmpty()
    }
}
