package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NARINGSDRIVENDE_NY_I_ARBEIDSLIVET
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class InntektsopplysningerDokumenterTest {
    @Test
    fun `Beregn dokumenter som skal sendes inn når Søknad sendes inn før 31 mai`() {
        val testDato = LocalDate.of(2023, 5, 30)
        val forventet =
            listOf(
                InntektsopplysningerDokumentType.REGNSKAP_FORRIGE_AAR,
                InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON_OPTIONAL,
            )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Beregn dokumenter som skal sendes inn når søknad sendes inn etter 31 mai men før starten på siste tertial`() {
        val testDato = LocalDate.of(2023, 6, 1)
        val forventet =
            listOf(
                InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON,
            )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Beregn dokumenter som skal sendes inn når søknad sendes inn i siste tertial`() {
        val testDato = LocalDate.of(2023, 9, 1)
        val forventet =
            listOf(
                InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON,
                InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG,
            )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Må dokumentere inntektsopplysninger når man er ny i arbeidslivet nytt sporsmal`() {
        val soknad =
            lagSoknad(
                mapOf(
                    NARINGSDRIVENDE_NY_I_ARBEIDSLIVET to "JA",
                ),
            )

        soknad.inntektsopplysningerMaaDokumenteres() shouldBeEqualTo true
    }

    private fun lagSoknad(sporsmalOgSvar: Map<String, String>): Sykepengesoknad =
        lagSoknadMedSporsmal(
            sporsmalOgSvar.map { (tag, svar) ->
                Sporsmal(
                    tag = tag,
                    // Vi bryr oss ikke om svartype i denne testen, så vi kan iterer over Map.
                    svartype = Svartype.JA_NEI,
                    svar = listOf(Svar("1", svar)),
                )
            },
        )

    private fun lagSoknadMedSporsmal(sporsmal: List<Sporsmal>): Sykepengesoknad =
        Sykepengesoknad(
            fnr = "11111111111",
            id = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
            arbeidsgiverOrgnummer = "11111111111",
            startSykeforlop = LocalDate.now(),
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            soknadstype = Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            status = Soknadstatus.NY,
            egenmeldingsdagerFraSykmelding = null,
            utenlandskSykmelding = false,
            opprettet = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
            soknadPerioder = emptyList(),
            sporsmal = sporsmal,
            sykmeldingSkrevet = Instant.now(),
            forstegangssoknad = true,
        )
}
