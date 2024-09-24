package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

class AaregHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var aaregDataHenting: AaregDataHenting

    @Test
    fun `finner ingen nye arbeidsforhold`() {
        aaregDataHenting.hentNyeArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333666",
            startSykeforlop = LocalDate.of(2024, 9, 15),
            sykepengesoknadId = UUID.randomUUID().toString(),
            soknadTom = LocalDate.of(2024, 9, 15),
        ).`should be empty`()
    }

    @Test
    fun `finner to nye arbeidsforhold`() {
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = "22222220001",
                arbeidsgiverOrgnummer = "112233445",
                startSykeforlop = LocalDate.of(2024, 9, 15).minusDays(50),
                sykepengesoknadId = UUID.randomUUID().toString(),
                soknadTom = LocalDate.of(2024, 9, 15),
            )
        nyeArbeidsforhold.shouldHaveSize(2)
        nyeArbeidsforhold[0].arbeidsstedNavn `should be equal to` "Bensinstasjonen AS"
        nyeArbeidsforhold[1].arbeidsstedNavn `should be equal to` "Kiosken, avd Oslo AS"
    }

    @Test
    fun `tar ikke med arbeidsforhold som er oppstått etter søknadens tom`() {
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = "22222220001",
                arbeidsgiverOrgnummer = "112233445",
                startSykeforlop = LocalDate.of(2024, 9, 15).minusDays(50),
                sykepengesoknadId = UUID.randomUUID().toString(),
                soknadTom = LocalDate.of(2024, 9, 15).minusDays(42),
            )
        nyeArbeidsforhold.shouldBeEmpty()
    }

    @Test
    fun `filtrerer ut arbeidsforhold med interne orgnummer bytte (samme opplysningspliktig orgnummer)`() {
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = "44444444999",
                arbeidsgiverOrgnummer = "999333666",
                startSykeforlop = LocalDate.of(2024, 9, 15).minusDays(50),
                sykepengesoknadId = UUID.randomUUID().toString(),
                soknadTom = LocalDate.of(2024, 9, 15),
            )
        nyeArbeidsforhold.shouldHaveSize(1)
        nyeArbeidsforhold[0].arbeidsstedNavn `should be equal to` "Frilanseransetter AS"
    }
}
