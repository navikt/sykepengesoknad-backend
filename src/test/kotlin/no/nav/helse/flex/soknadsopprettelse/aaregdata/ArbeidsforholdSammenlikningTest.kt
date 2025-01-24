package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ArbeidsforholdSammenlikningTest {
    // Hjelpefunksjoner for å lage testdata raskt
    private fun defaultIdent(ident: String = "123456789"): Ident = Ident(type = "ORGNR", ident = ident, gjeldende = true)

    private fun defaultKodeverk(
        kode: String,
        beskrivelse: String = "",
    ): Kodeverksentitet = Kodeverksentitet(kode, beskrivelse)

    private fun defaultOpplysningspliktig(
        type: String = "ORG",
        ident: String = "999999999",
    ): Opplysningspliktig = Opplysningspliktig(type = type, identer = listOf(defaultIdent(ident)))

    private fun defaultArbeidssted(
        type: String = "BEDR",
        ident: String = "888888888",
    ): Arbeidssted = Arbeidssted(type = type, identer = listOf(defaultIdent(ident)))

    private fun defaultAnsettelsesperiode(
        startdato: LocalDate,
        sluttdato: LocalDate? = null,
        sluttaarsak: Kodeverksentitet? = null,
    ): Ansettelsesperiode =
        Ansettelsesperiode(
            startdato = startdato,
            sluttdato = sluttdato,
            sluttaarsak = sluttaarsak,
            varsling = null,
        )

    private fun defaultAnsettelsesdetaljer(
        fra: YearMonth,
        til: YearMonth? = null,
        yrkeskode: String = "1234",
        type: String = "FAST",
        stillingsprosent: Double? = 100.0,
    ): Ansettelsesdetaljer =
        Ansettelsesdetaljer(
            type = type,
            yrke = Kodeverksentitet(yrkeskode, ""),
            avtaltStillingsprosent = stillingsprosent,
            rapporteringsmaaneder = Rapporteringsmaaneder(fra = fra, til = til),
        )

    private fun lagArbeidsforhold(
        opplysningspliktig: Opplysningspliktig,
        arbeidssted: Arbeidssted,
        start: LocalDate,
        slutt: LocalDate? = null,
        sluttaarsak: Kodeverksentitet? = null,
        ansettelsesdetaljer: List<Ansettelsesdetaljer> =
            listOf(
                defaultAnsettelsesdetaljer(YearMonth.of(2021, 1), YearMonth.of(2021, 12)),
            ),
    ): Arbeidsforhold {
        return Arbeidsforhold(
            id = null,
            type = defaultKodeverk("type", "Arbeidsforholdstype"),
            arbeidstaker = Arbeidstaker(identer = listOf(defaultIdent("12345678901"))),
            arbeidssted = arbeidssted,
            opplysningspliktig = opplysningspliktig,
            ansettelsesperiode = defaultAnsettelsesperiode(start, slutt, sluttaarsak),
            ansettelsesdetaljer = ansettelsesdetaljer,
            opprettet = LocalDateTime.now(),
        )
    }

    @Test
    fun `erGanskeRettEtterHverandre - skal vaere true naar differanse er mindre eller lik 4 dager`() {
        val arbeidsforhold1 =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig(),
                arbeidssted = defaultArbeidssted(),
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 1, 10),
            )
        val arbeidsforhold2 =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig(),
                arbeidssted = defaultArbeidssted(),
                start = LocalDate.of(2021, 1, 14),
            )
        (arbeidsforhold1 to arbeidsforhold2).erGanskeRettEtterHverandre().shouldBeTrue()
    }

    @Test
    fun `erGanskeRettEtterHverandre - skal vaere false naar differanse er over 4 dager`() {
        val arbeidsforhold1 =
            lagArbeidsforhold(
                defaultOpplysningspliktig(),
                defaultArbeidssted(),
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 1, 10),
            )
        // 5 dagers gap
        val arbeidsforhold2 =
            lagArbeidsforhold(
                defaultOpplysningspliktig(),
                defaultArbeidssted(),
                start = LocalDate.of(2021, 1, 15),
            )
        (arbeidsforhold1 to arbeidsforhold2).erGanskeRettEtterHverandre().shouldBeFalse()
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `erMestSannsynligVidereforingAv - happy case, samme opplysningspliktig, sluttaarsak endringIOrganisasjonsstrukturEllerByttetJobbInternt, erGanskeRettEtterHverandre`() {
        val oppl = defaultOpplysningspliktig("ORG", "999999999")
        val sted = defaultArbeidssted("BEDR", "888888888")

        val forrige =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 6, 1),
                sluttaarsak = defaultKodeverk("endringIOrganisasjonsstrukturEllerByttetJobbInternt"),
            )

        val nytt =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = defaultArbeidssted("BEDR", "888888888"),
                start = LocalDate.of(2021, 6, 2),
            )

        nytt.erMestSannsynligVidereføringAv(forrige).shouldBeTrue()
    }

    @Test
    fun `erMestSannsynligVidereforingAv - skal vaere false dersom opplysningspliktig er ulik`() {
        val forrige =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig("ORG", "999999999"),
                arbeidssted = defaultArbeidssted("BEDR", "888888888"),
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 2, 1),
            )
        val nytt =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig("ORG", "111111111"),
                arbeidssted = defaultArbeidssted("BEDR", "888888888"),
                start = LocalDate.of(2021, 2, 2),
            )

        nytt.erMestSannsynligVidereføringAv(forrige).shouldBeFalse()
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `erMestSannsynligVidereforingAv - skal vaere true ved byttetLoenssystemEllerRegnskapsfoerer, samme arbeidssted, erGanskeRettEtterHverandre`() {
        val oppl = defaultOpplysningspliktig("ORG", "999999999")
        val sted = defaultArbeidssted("BEDR", "888888888")

        val forrige =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 3, 1),
                sluttaarsak = defaultKodeverk("byttetLoenssystemEllerRegnskapsfoerer"),
            )

        val nytt =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 3, 2),
            )

        nytt.erMestSannsynligVidereføringAv(forrige).shouldBeTrue()
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `kanKanskjeVaereVidereforingAv - skal vaere true med riktig sluttaarsak, erGanskeRettEtterHverandre og ansettelsesdetaljer er GanskeLik`() {
        val oppl = defaultOpplysningspliktig("ORG", "999999999")
        val sted = defaultArbeidssted("BEDR", "888888888")

        val forrige =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 3, 31),
                sluttaarsak = defaultKodeverk("endringIOrganisasjonsstrukturEllerByttetJobbInternt"),
                ansettelsesdetaljer =
                    listOf(
                        defaultAnsettelsesdetaljer(
                            fra = YearMonth.of(2021, 1),
                            til = YearMonth.of(2021, 3),
                            yrkeskode = "1234",
                            type = "FAST",
                            stillingsprosent = 100.0,
                        ),
                    ),
            )

        // Nytt arbeidsforhold starter 1 dag etter slutt
        val nytt =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 4, 1),
                ansettelsesdetaljer =
                    listOf(
                        // Viktig at disse detaljene "matcher" (erGanskeLik)
                        defaultAnsettelsesdetaljer(
                            fra = YearMonth.of(2021, 4),
                            til = null,
                            yrkeskode = "1234",
                            type = "FAST",
                            stillingsprosent = 100.0,
                        ),
                    ),
            )

        nytt.kanKanskjeVæreVidereføringAv(forrige).shouldBeTrue()
    }

    @Test
    fun `kanKanskjeVaereVidereforingAv - skal vaere false dersom en av betingelsene ikke er oppfylt`() {
        // F.eks. feil sluttaarsak
        val forrige =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig(),
                arbeidssted = defaultArbeidssted(),
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 3, 31),
                sluttaarsak = defaultKodeverk("kontraktEngasjementEllerVikariatErUtloept"),
                ansettelsesdetaljer =
                    listOf(
                        defaultAnsettelsesdetaljer(
                            fra = YearMonth.of(2021, 1),
                            til = YearMonth.of(2021, 3),
                        ),
                    ),
            )

        val nytt =
            lagArbeidsforhold(
                opplysningspliktig = defaultOpplysningspliktig(),
                arbeidssted = defaultArbeidssted(),
                start = LocalDate.of(2021, 4, 1),
                ansettelsesdetaljer =
                    listOf(
                        defaultAnsettelsesdetaljer(
                            fra = YearMonth.of(2021, 4),
                            til = null,
                        ),
                    ),
            )

        nytt.kanKanskjeVæreVidereføringAv(forrige).shouldBeFalse()
    }

    @Test
    fun `erIkkeVidereforingAvAnnetArbeidsforhold - skal være true når ingen i lista er videreforing`() {
        val f1 =
            lagArbeidsforhold(
                defaultOpplysningspliktig("ORG", "999999999"),
                defaultArbeidssted("BEDR", "888888888"),
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 2, 1),
            )
        val f2 =
            lagArbeidsforhold(
                defaultOpplysningspliktig("ORG", "111111111"),
                defaultArbeidssted("BEDR", "777777777"),
                start = LocalDate.of(2021, 2, 2),
            )
        val f3 =
            lagArbeidsforhold(
                defaultOpplysningspliktig("ORG", "222222222"),
                defaultArbeidssted("BEDR", "666666666"),
                start = LocalDate.of(2021, 3, 1),
            )

        // f2 og f3 ikke videreføring av f1
        // f1 er ikke videreføring av f2 eller f3 osv.
        val alle = listOf(f1, f2, f3)

        f1.erIkkeVidereforingAvAnnetArbeidsforhold(alle).shouldBeTrue()
        f2.erIkkeVidereforingAvAnnetArbeidsforhold(alle).shouldBeTrue()
        f3.erIkkeVidereforingAvAnnetArbeidsforhold(alle).shouldBeTrue()
    }

    @Test
    fun `erIkkeVidereforingAvAnnetArbeidsforhold - skal vaere false naar en i lista er videreforing`() {
        val oppl = defaultOpplysningspliktig("ORG", "999999999")
        val sted = defaultArbeidssted("BEDR", "888888888")

        val f1 =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 1, 1),
                slutt = LocalDate.of(2021, 1, 31),
                sluttaarsak = defaultKodeverk("endringIOrganisasjonsstrukturEllerByttetJobbInternt"),
            )

        val f2 =
            lagArbeidsforhold(
                opplysningspliktig = oppl,
                arbeidssted = sted,
                start = LocalDate.of(2021, 2, 1),
            )
        val alle = listOf(f1, f2)

        // f2 er mest sannsynlig en videreføring av f1
        f2.erIkkeVidereforingAvAnnetArbeidsforhold(alle).shouldBeFalse()
    }
}
