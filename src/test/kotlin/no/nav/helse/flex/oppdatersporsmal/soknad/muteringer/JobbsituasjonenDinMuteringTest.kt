package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.frisktilarbeid.settOppSykepengesoknadFriskmeldtTilArbeidsformidling
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

class JobbsituasjonenDinMuteringTest {
    /**
     * Hjelpefunksjon for å opprette en standard søknad av typen FRISKMELDT_TIL_ARBEIDSFORMIDLING
     * med spørsmålene fra settOppSykepengesoknadFriskmeldtTilArbeidsformidling().
     */
    private fun opprettStandardSoknad(
        fom: LocalDate,
        tom: LocalDate,
    ): Sykepengesoknad {
        val soknad =
            Sykepengesoknad(
                fnr = "12345678910",
                startSykeforlop = fom,
                fom = fom,
                tom = tom,
                arbeidssituasjon = null,
                arbeidsgiverOrgnummer = null,
                arbeidsgiverNavn = null,
                sykmeldingId = UUID.randomUUID().toString(),
                soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                sykmeldingSkrevet = null,
                soknadPerioder = emptyList(),
                egenmeldtSykmelding = null,
                id = UUID.randomUUID().toString(),
                status = Soknadstatus.NY,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                egenmeldingsdagerFraSykmelding = null,
                forstegangssoknad = false,
            )
        // Sett opp spørsmål for friskmeldt-til-arbeidsformidling
        return soknad.copy(
            sporsmal =
                settOppSykepengesoknadFriskmeldtTilArbeidsformidling(
                    SettOppSoknadOptions(
                        sykepengesoknad = soknad,
                        erForsteSoknadISykeforlop = true,
                        harTidligereUtenlandskSpm = false,
                        yrkesskade =
                            YrkesskadeSporsmalGrunnlag(
                                emptyList(),
                            ),
                        eksisterendeSoknader = emptyList(),
                    ),
                    Periode(fom, tom.plusDays(14)),
                ),
        )
    }

    @Test
    fun `søknaden returneres uendret dersom spørsmålet med tag FTA_JOBBSITUASJONEN_DIN mangler`() {
        // Opprett en søknad med spm som ikke inneholder FTA_JOBBSITUASJONEN_DIN
        val soknad =
            opprettStandardSoknad(LocalDate.now(), LocalDate.now().plusDays(10))
                .copy(sporsmal = listOf(ansvarserklaringSporsmal(), tilSlutt()))
        val mutertSoknad = soknad.jobbsituasjonenDinMutering()

        // Siden hovedspørsmålet mangler, skal ingen mutering skje
        mutertSoknad.sporsmal `should be equal to` soknad.sporsmal
    }

    @Test
    fun `søknaden returneres uendret dersom ingen begrensende dato finnes i FTA-jobbsituasjonen underspørsmål`() {
        val soknad = opprettStandardSoknad(LocalDate.now(), LocalDate.now().plusDays(10))
        // La underspørsmålene ligge uendret (ingen av JA eller NEI er besvart)
        val soknadUtenSvar = soknad

        val mutertSoknad = soknadUtenSvar.jobbsituasjonenDinMutering()
        // Ingen begrensende dato – muteringen skal ikke endre spørsmålene
        mutertSoknad.sporsmal `should be equal to` soknadUtenSvar.sporsmal
    }

    @Test
    fun `mutering legger til nye inntekt- og reise-spørsmål ved JA-svar med gyldig begrensende dato`() {
        val fom = LocalDate.of(2025, 2, 1)
        val tom = fom.plusDays(10)
        val soknad = opprettStandardSoknad(fom, tom)

        // Simuler at brukeren har svart:
        // - FTA_JOBBSITUASJONEN_DIN_JA: CHECKED
        // - FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB: NEI
        // - FTA_JOBBSITUASJONEN_DIN_NAR: en dato som er etter fom (f.eks. fom + 5 dager)
        val soknadMedSvar =
            soknad
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, fom.plusDays(5).format(ISO_LOCAL_DATE))
        // Forventet: siden begrensende dato (fom+5) ≠ fom, skal de to spm legges til.

        // Før mutering finnes spm for inntekt og reise (fra oppsettet)
        soknadMedSvar.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Hadde du  inntekt i perioden 1. - 11. februar 2025?"
        soknadMedSvar.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 11. februar 2025?"

        // Muter søknaden
        val mutertSoknad = soknadMedSvar.jobbsituasjonenDinMutering()
        // Etter mutering skal de to gamle spm være fjernet og erstattet med nye med andre datoer
        mutertSoknad.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Hadde du  inntekt i perioden 1. - 5. februar 2025?"
        mutertSoknad.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 5. februar 2025?"
        mutertSoknad.sporsmal.shouldHaveSize(5)

        mutertSoknad.sporsmal.map { it.tag }
            .filter { it.contains(FTA_INNTEKT_UNDERVEIS) || it.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(2)

        // spørsmål muteres tilbake
        val remutert =
            mutertSoknad
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "JA")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, fom.plusDays(5).format(ISO_LOCAL_DATE))
                .jobbsituasjonenDinMutering()

        remutert.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Hadde du  inntekt i perioden 1. - 11. februar 2025?"
        remutert.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 11. februar 2025?"
    }

    @Test
    fun `mutering legger ikke til nye inntekt- og reise-spørsmål dersom begrensende dato er lik fom`() {
        val fom = LocalDate.of(2025, 2, 1)
        val tom = fom.plusDays(10)
        val soknad = opprettStandardSoknad(fom, tom)

        // Simuler svar i JA-grenen, men der begrensende dato er lik fom
        val soknadMedSvar =
            soknad
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, fom.format(ISO_LOCAL_DATE))
        // I oppsettet fra settOppSykepengesoknadFriskmeldtTilArbeidsformidling finnes allerede spm for inntekt og reise.
        // Muteringen fjerner disse, men siden dato er lik fom, legges ingen nye inn.
        val mutertSoknad = soknadMedSvar.jobbsituasjonenDinMutering()

        // Forvent at spørsmål med tag FTA_INNTEKT_UNDERVEIS og FTA_REISE_TIL_UTLANDET ikke finnes i den muterte søknaden
        mutertSoknad.sporsmal.find { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }.shouldBeNull()
        mutertSoknad.sporsmal.find { it.tag.contains(FTA_REISE_TIL_UTLANDET) }.shouldBeNull()

        // Siden de to spm fjernes, skal totalt antall spørsmål være 3 (forutsatt at oppsettet opprinnelig hadde 5 spm)
        mutertSoknad.sporsmal.shouldHaveSize(3)

        // spørsmål muteres tilbake
        val remutert =
            mutertSoknad
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "JA")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_NAR, fom.plusDays(5).format(ISO_LOCAL_DATE))
                .jobbsituasjonenDinMutering()

        remutert.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Hadde du  inntekt i perioden 1. - 11. februar 2025?"
        remutert.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 11. februar 2025?"
    }

    @Test
    fun `mutering legger til nye inntekt- og reise-spørsmål ved NEI-svar med gyldig begrensende dato`() {
        val fom = LocalDate.of(2025, 2, 1)
        val tom = fom.plusDays(10)
        val soknad = opprettStandardSoknad(fom, tom)

        // Simuler NEI-grenen:
        // - FTA_JOBBSITUASJONEN_DIN_NEI: CHECKED
        // - FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT: NEI
        // - FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR: en dato etter fom
        val soknadMedSvar =
            soknad
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI")
                .besvarsporsmal(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR, fom.plusDays(7).format(ISO_LOCAL_DATE))
        // Forventet: siden dato (fom+7) ≠ fom, skal de to nye spørsmålene legges til

        // Før mutering finnes spm for inntekt og reise (fra oppsettet)
        soknadMedSvar.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }
            .first() `should be equal to` "Hadde du  inntekt i perioden 1. - 11. februar 2025?"
        soknadMedSvar.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }
            .first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 11. februar 2025?"

        val mutertSoknad = soknadMedSvar.jobbsituasjonenDinMutering()

        // Etter mutering skal de to gamle spm være fjernet og erstattet med nye men de er like
        mutertSoknad.sporsmal
            .filter { it.tag.contains(FTA_INNTEKT_UNDERVEIS) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }
            .first() `should be equal to` "Hadde du  inntekt i perioden 1. - 7. februar 2025?"
        mutertSoknad.sporsmal
            .filter { it.tag.contains(FTA_REISE_TIL_UTLANDET) }
            .shouldHaveSize(1)
            .map { it.sporsmalstekst }.first() `should be equal to` "Var du på reise utenfor EU/EØS i perioden 1. - 7. februar 2025?"
        mutertSoknad.sporsmal.shouldHaveSize(5)
    }
}
