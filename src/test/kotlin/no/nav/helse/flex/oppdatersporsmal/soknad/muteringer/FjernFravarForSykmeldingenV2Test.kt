package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.mock.opprettNyNaeringsdrivendeSoknadGradert
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_V2
import org.junit.jupiter.api.Test

class FjernFravarForSykmeldingenV2Test {
    @Test
    fun `Soknad uten FRAVAR_FOR_SYKMELDINGEN_V2 skal ikke endres`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert()
        soknad.getSporsmalMedTagOrNull(FRAVAR_FOR_SYKMELDINGEN_V2) shouldBe null

        val mutertSoknad = soknad.fjernFravaerFoerSykmeldingenV2()

        mutertSoknad shouldBe soknad
    }

    @Test
    fun `Soknad med FRAVAR_FOR_SYKMELDINGEN_V2 skal få spørsmålet fjernet`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert().leggTilSporsmaal(fravarForSykmeldingenV2Sporsmal())
        soknad.getSporsmalMedTagOrNull(FRAVAR_FOR_SYKMELDINGEN_V2) shouldNotBe null

        val mutertSoknad = soknad.fjernFravaerFoerSykmeldingenV2()

        mutertSoknad.getSporsmalMedTagOrNull(FRAVAR_FOR_SYKMELDINGEN_V2) shouldBe null
        mutertSoknad.sporsmal.size shouldBe soknad.sporsmal.size - 1
        mutertSoknad.sporsmal.map { it.tag } shouldBe
            soknad.sporsmal.map { it.tag }.filterNot { it == FRAVAR_FOR_SYKMELDINGEN_V2 }
    }

    @Test
    fun `Mutering kan kjøres flere ganger uten feil`() {
        val soknad = opprettNyNaeringsdrivendeSoknadGradert().leggTilSporsmaal(fravarForSykmeldingenV2Sporsmal())

        val mutertSoknad = soknad.fjernFravaerFoerSykmeldingenV2()
        val dobbeltMutertSoknad = mutertSoknad.fjernFravaerFoerSykmeldingenV2()

        dobbeltMutertSoknad shouldBe mutertSoknad
    }
}

private fun fravarForSykmeldingenV2Sporsmal() =
    Sporsmal(
        tag = FRAVAR_FOR_SYKMELDINGEN_V2,
        sporsmalstekst = "Var du borte fra jobb i fire uker eller mer rett før du ble sykmeldt?",
        svartype = Svartype.JA_NEI,
        undertekst = "Gjelder sammenhengende ferie eller annet fravær gjennom alle fire ukene. Har du jobbet underveis, kan du svare nei.",
    )
