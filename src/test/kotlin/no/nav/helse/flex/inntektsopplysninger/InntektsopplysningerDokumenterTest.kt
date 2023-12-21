package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT
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
        val forventet = listOf(
            InntektsopplysningerDokumentType.REGNSKAP_FORRIGE_AAR,
            InntektsopplysningerDokumentType.SKATTEMELDING_OPTIONAL,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON_OPTIONAL
        )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Beregn dokumenter som skal sendes inn når søknad sendes inn etter 31 mai men før starten på siste tertial`() {
        val testDato = LocalDate.of(2023, 6, 1)
        val forventet = listOf(
            InntektsopplysningerDokumentType.SKATTEMELDING,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON
        )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Beregn dokumenter som skal sendes inn når søknad sendes inn i siste tertial`() {
        val testDato = LocalDate.of(2023, 9, 1)
        val forventet = listOf(
            InntektsopplysningerDokumentType.SKATTEMELDING,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON,
            InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG
        )
        assertEquals(forventet, dokumenterSomSkalSendesInn(testDato))
    }

    @Test
    fun `Må dokumentere inntektsopplysninger når man er ny i arbeidslivet`() {
        val soknad = lagSoknad(
            mapOf(
                INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA to "CHECKED"
            )
        )

        soknad.inntektsopplysningerMaaDokumenteres() shouldBeEqualTo true
    }

    @Test
    fun `Må dokumentere inntektsopplysninger når ikke er ny i arbeidslivet men varig endring er over 25 prosent`() {
        val soknad = lagSoknad(
            mapOf(
                INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI to "CHECKED",
                INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT to "JA"
            )
        )

        soknad.inntektsopplysningerMaaDokumenteres() shouldBeEqualTo true
    }

    @Test
    fun `Må ikke dokumenere inntektsopplysninger når ikke er ny i arbeidslivet og varig endring er under25 prosent`() {
        val soknad = lagSoknad(
            mapOf(
                INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI to "CHECKED",
                INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT to "NEI"
            )
        )

        soknad.inntektsopplysningerMaaDokumenteres() shouldBeEqualTo false
    }

    private fun lagSoknad(sporsmalOgSvar: Map<String, String>): Sykepengesoknad {
        return lagSoknadMedSporsmal(
            sporsmalOgSvar.map { (tag, svar) ->
                Sporsmal(
                    tag = tag,
                    // Vi bryr oss ikke om svartype i denne testen, så vi kan iterer over Map.
                    svartype = Svartype.JA_NEI,
                    svar = listOf(Svar("1", svar))
                )
            }
        )
    }

    private fun lagSoknadMedSporsmal(
        sporsmal: List<Sporsmal>
    ): Sykepengesoknad {
        return Sykepengesoknad(
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
            forstegangssoknad = true
        )
    }
}
