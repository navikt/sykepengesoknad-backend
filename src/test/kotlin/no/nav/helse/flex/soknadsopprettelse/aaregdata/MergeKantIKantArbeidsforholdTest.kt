package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MergeKantIKantArbeidsforholdTest {
    @Test
    fun `skal returnere tom liste hvis input er tom`() {
        val emptyList = emptyList<ArbeidsforholdOversikt>()
        assertTrue(emptyList.mergeKantIKant().isEmpty())
    }

    @Test
    fun `skal ikke merge dersom de ikke er like arbeidssted eller opplysningspliktig`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
                arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "123"))),
                opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
                arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "124"))),
                opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
            )

        val result = listOf(a1, a2).mergeKantIKant()
        // Ikke samme arbeidssted, så to separate grupper
        assertEquals(2, result.size)
    }

    @Test
    fun `skal merge hvis like og startdato for neste er dagen etter sluttdato for forrige`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
                avtaltProsent = 60,
            )

        val result = listOf(a1, a2).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.startdato)
        assertEquals(LocalDate.of(2020, 1, 20), merged.sluttdato)
        // Avtalt stillingsprosent hentes fra siste i kjeden
        assertEquals(60, merged.avtaltStillingsprosent)
    }

    @Test
    fun `skal merge hvis start og slutt samme dag`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
                avtaltProsent = 60,
            )

        val result = listOf(a1, a2).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.startdato)
        assertEquals(LocalDate.of(2020, 1, 20), merged.sluttdato)
        // Avtalt stillingsprosent hentes fra siste i kjeden
        assertEquals(60, merged.avtaltStillingsprosent)
    }

    @Test
    fun `skal merge flere etter hverandre`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
                avtaltProsent = 40,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 6),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
            )
        val a3 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 15),
                avtaltProsent = 60,
            )

        val result = listOf(a1, a2, a3).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.startdato)
        assertEquals(LocalDate.of(2020, 1, 15), merged.sluttdato)
        assertEquals(60, merged.avtaltStillingsprosent)
    }

    @Test
    fun `skal ikke merge dersom det er mer enn en dag mellom perioder`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
                avtaltProsent = 40,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 7),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
            ) // 2 dager etter slutt på a1

        val result = listOf(a1, a2).mergeKantIKant()
        // Samme gruppe, men kan ikke merges pga 2 dagers gap.
        assertEquals(2, result.size)
    }

    @Test
    fun `skal ikke merge hvis forrige har null sluttdato`() {
        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = null,
                avtaltProsent = 40,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 2),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
            )

        val result = listOf(a1, a2).mergeKantIKant()
        // a1 har ingen sluttdato, her velger vi å ikke merge.
        assertEquals(2, result.size)
    }

    @Test
    fun `skal merge i hver sin gruppe selv om periodene er blandet`() {
        val arbSted1 = Arbeidssted("ORG", listOf(Ident("ORG", "123")))
        val opplys1 = Opplysningspliktig("ORG", listOf(Ident("ORG", "999")))

        val arbSted2 = Arbeidssted("ORG", listOf(Ident("ORG", "456")))
        val opplys2 = Opplysningspliktig("ORG", listOf(Ident("ORG", "888")))

        val a1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
                avtaltProsent = 40,
                arbeidssted = arbSted1,
                opplysningspliktig = opplys1,
            )
        val a2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 6),
                slutt = LocalDate.of(2020, 1, 10),
                avtaltProsent = 50,
                arbeidssted = arbSted1,
                opplysningspliktig = opplys1,
            )
        val b1 =
            arbeidsforhold(
                start = LocalDate.of(2019, 12, 1),
                slutt = LocalDate.of(2019, 12, 10),
                avtaltProsent = 100,
                arbeidssted = arbSted2,
                opplysningspliktig = opplys2,
            )
        val b2 =
            arbeidsforhold(
                start = LocalDate.of(2019, 12, 11),
                slutt = LocalDate.of(2019, 12, 20),
                avtaltProsent = 80,
                arbeidssted = arbSted2,
                opplysningspliktig = opplys2,
            )

        // Liste i "rotete" rekkefølge
        val result = listOf(a2, b1, a1, b2).mergeKantIKant()

        // Etter merging skal vi få to merged-forhold, ett for gruppe1 (a1,a2) og ett for gruppe2 (b1,b2)
        assertEquals(2, result.size)

        val group1Merged = result.find { it.arbeidssted == arbSted1 && it.opplysningspliktig == opplys1 }!!
        assertEquals(LocalDate.of(2020, 1, 1), group1Merged.startdato)
        assertEquals(LocalDate.of(2020, 1, 10), group1Merged.sluttdato)
        assertEquals(50, group1Merged.avtaltStillingsprosent)

        val group2Merged = result.find { it.arbeidssted == arbSted2 && it.opplysningspliktig == opplys2 }!!
        assertEquals(LocalDate.of(2019, 12, 1), group2Merged.startdato)
        assertEquals(LocalDate.of(2019, 12, 20), group2Merged.sluttdato)
        assertEquals(80, group2Merged.avtaltStillingsprosent)
    }

    private fun arbeidsforhold(
        start: LocalDate,
        slutt: LocalDate? = null,
        arbeidssted: Arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "123"))),
        opplysningspliktig: Opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
        yrke: Kodeverksentitet = Kodeverksentitet("YRKE", "someYrke"),
        avtaltProsent: Int = 100,
        permisjonsprosent: Int? = null,
        permitteringsprosent: Int? = null,
    ): ArbeidsforholdOversikt {
        return ArbeidsforholdOversikt(
            type = Kodeverksentitet("TYPE", "ArbForhold"),
            arbeidstaker = Arbeidstaker(listOf(Ident("fnr", "12345678910"))),
            arbeidssted = arbeidssted,
            opplysningspliktig = opplysningspliktig,
            startdato = start,
            sluttdato = slutt,
            yrke = yrke,
            avtaltStillingsprosent = avtaltProsent,
            permisjonsprosent = permisjonsprosent,
            permitteringsprosent = permitteringsprosent,
        )
    }
}
