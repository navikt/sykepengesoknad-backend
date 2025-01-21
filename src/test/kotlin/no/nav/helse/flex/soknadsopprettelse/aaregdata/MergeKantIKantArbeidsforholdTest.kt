package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MergeKantIKantArbeidsforholdTest {
    @Test
    fun `skal returnere tom liste hvis input er tom`() {
        val emptyList = emptyList<Arbeidsforhold>()
        assertTrue(emptyList.mergeKantIKant().isEmpty())
    }

    @Test
    fun `skal ikke merge dersom de ikke er like arbeidssted eller opplysningspliktig`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
                arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "123"))),
                opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
                arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "124"))),
                opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
            )

        val result = listOf(arbeidsforhold1, arbeidsforhold2).mergeKantIKant()
        // Ikke samme arbeidssted, så to separate grupper
        assertEquals(2, result.size)
    }

    @Test
    fun `skal merge hvis like og startdato for neste er dagen etter sluttdato for forrige`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
            )

        val result = listOf(arbeidsforhold1, arbeidsforhold2).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.ansettelsesperiode.startdato)
        assertEquals(LocalDate.of(2020, 1, 20), merged.ansettelsesperiode.sluttdato)
    }

    @Test
    fun `skal merge hvis start og slutt samme dag`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 10),
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 20),
            )

        val result = listOf(arbeidsforhold1, arbeidsforhold2).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.ansettelsesperiode.startdato)
        assertEquals(LocalDate.of(2020, 1, 20), merged.ansettelsesperiode.sluttdato)
    }

    @Test
    fun `skal merge flere etter hverandre`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 6),
                slutt = LocalDate.of(2020, 1, 10),
            )
        val a3 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 11),
                slutt = LocalDate.of(2020, 1, 15),
            )

        val result = listOf(arbeidsforhold1, arbeidsforhold2, a3).mergeKantIKant()
        assertEquals(1, result.size)
        val merged = result[0]
        assertEquals(LocalDate.of(2020, 1, 1), merged.ansettelsesperiode.startdato)
        assertEquals(LocalDate.of(2020, 1, 15), merged.ansettelsesperiode.sluttdato)
    }

    @Test
    fun `skal ikke merge dersom det er mer enn en dag mellom perioder`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 7),
                slutt = LocalDate.of(2020, 1, 10),
            ) // 2 dager etter slutt på arbeidsforhold1

        val result = listOf(arbeidsforhold1, arbeidsforhold2).mergeKantIKant()
        // Samme gruppe, men kan ikke merges pga 2 dagers gap.
        assertEquals(2, result.size)
    }

    @Test
    fun `skal ikke merge hvis forrige har null sluttdato`() {
        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = null,
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 2),
                slutt = LocalDate.of(2020, 1, 10),
            )

        val result = listOf(arbeidsforhold1, arbeidsforhold2).mergeKantIKant()
        // arbeidsforhold1 har ingen sluttdato, her velger vi å ikke merge.
        assertEquals(2, result.size)
    }

    @Test
    fun `skal merge i hver sin gruppe selv om periodene er blandet`() {
        val arbeidssted1 = Arbeidssted("ORG", listOf(Ident("ORG", "123")))
        val opplysningspliktig1 = Opplysningspliktig("ORG", listOf(Ident("ORG", "999")))

        val arbeidssted2 = Arbeidssted("ORG", listOf(Ident("ORG", "456")))
        val opplysningspliktig2 = Opplysningspliktig("ORG", listOf(Ident("ORG", "888")))

        val arbeidsforhold1 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 1),
                slutt = LocalDate.of(2020, 1, 5),
                arbeidssted = arbeidssted1,
                opplysningspliktig = opplysningspliktig1,
            )
        val arbeidsforhold2 =
            arbeidsforhold(
                start = LocalDate.of(2020, 1, 6),
                slutt = LocalDate.of(2020, 1, 10),
                arbeidssted = arbeidssted1,
                opplysningspliktig = opplysningspliktig1,
            )
        val arbeidsforhold3 =
            arbeidsforhold(
                start = LocalDate.of(2019, 12, 1),
                slutt = LocalDate.of(2019, 12, 10),
                arbeidssted = arbeidssted2,
                opplysningspliktig = opplysningspliktig2,
            )
        val arbeidsforhold4 =
            arbeidsforhold(
                start = LocalDate.of(2019, 12, 11),
                slutt = LocalDate.of(2019, 12, 20),
                arbeidssted = arbeidssted2,
                opplysningspliktig = opplysningspliktig2,
            )

        // Liste i "rotete" rekkefølge
        val result = listOf(arbeidsforhold2, arbeidsforhold3, arbeidsforhold1, arbeidsforhold4).mergeKantIKant()

        // Etter merging skal vi få to merged-forhold, ett for gruppe1 (arbeidsforhold1,arbeidsforhold2) og ett for gruppe2 (arbeidsforhold3,arbeidsforhold4)
        assertEquals(2, result.size)

        val group1Merged =
            result.find { it.arbeidssted == arbeidssted1 && it.opplysningspliktig == opplysningspliktig1 }!!
        assertEquals(LocalDate.of(2020, 1, 1), group1Merged.ansettelsesperiode.startdato)
        assertEquals(LocalDate.of(2020, 1, 10), group1Merged.ansettelsesperiode.sluttdato)

        val group2Merged =
            result.find { it.arbeidssted == arbeidssted2 && it.opplysningspliktig == opplysningspliktig2 }!!
        assertEquals(LocalDate.of(2019, 12, 1), group2Merged.ansettelsesperiode.startdato)
        assertEquals(LocalDate.of(2019, 12, 20), group2Merged.ansettelsesperiode.sluttdato)
    }

    private fun arbeidsforhold(
        start: LocalDate,
        slutt: LocalDate? = null,
        arbeidssted: Arbeidssted = Arbeidssted("ORG", listOf(Ident("ORG", "123"))),
        opplysningspliktig: Opplysningspliktig = Opplysningspliktig("ORG", listOf(Ident("ORG", "999"))),
        ansettelsesdetaljer: List<Ansettelsesdetaljer> = emptyList()
    ): Arbeidsforhold {
        return Arbeidsforhold(
            type = Kodeverksentitet("TYPE", "ArbForhold"),
            arbeidstaker = Arbeidstaker(listOf(Ident("fnr", "12345678910"))),
            arbeidssted = arbeidssted,
            opplysningspliktig = opplysningspliktig,
            ansettelsesdetaljer = ansettelsesdetaljer,
            ansettelsesperiode =
                Ansettelsesperiode(
                    startdato = start,
                    sluttdato = slutt,
                ),
        )
    }
}
