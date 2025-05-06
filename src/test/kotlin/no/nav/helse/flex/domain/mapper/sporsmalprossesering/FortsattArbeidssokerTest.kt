package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_JA
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_NEI
import no.nav.helse.flex.soknadsopprettelse.frisktilarbeid.jobbsituasjonenDin
import no.nav.helse.flex.testutil.byttSvar
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FortsattArbeidssokerTest {
    @Test
    fun `Retunerer null ved ingen spørsmål`() {
        soknadMedSporsmal(emptyList()).fortsattFriskmeldtTilArbeidsformidling() shouldBeEqualTo null
    }

    @Test
    fun `Retunerer true ved ny jobb og og fortsatt arbeidssoker`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now())
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "JA")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling()!!.shouldBeTrue()
    }

    @Test
    fun `Retunerer false ved ny jobb og og ikke fortsatt arbeidssoker`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now())
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling()!!.shouldBeFalse()
    }

    @Test
    fun `Retunerer true ved ingen ny jobb og og fortsatt arbeidssoker`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now())
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED")
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "JA")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling()!!.shouldBeTrue()
    }

    @Test
    fun `Retunerer false ved ingen ny jobb og og ikke fortsatt arbeidssoker`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now())
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED")
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling()!!.shouldBeFalse()
    }

    @Test
    fun `Retunerer null ved ingen ny jobb på siste søknad`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now(), true)
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling().shouldBeNull()
    }

    @Test
    fun `Retunerer null ved ny jobb på siste søknad`() {
        val jobbsituasjonenDinSpm =
            jobbsituasjonenDin(LocalDate.now(), LocalDate.now(), true)
                .byttSvar(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")

        soknadMedSporsmal(listOf(jobbsituasjonenDinSpm)).fortsattFriskmeldtTilArbeidsformidling().shouldBeNull()
    }

    fun soknadMedSporsmal(sporsmal: List<Sporsmal>): Sykepengesoknad = opprettNySoknad().copy(sporsmal = sporsmal)
}
