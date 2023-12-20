package no.nav.helse.flex.inntektsopplysninger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HvilkeDokumenterTest {
    @Test
    fun `skal returnere regnskap for forrige år, skattemelding og næringsSpesifikasjon optional før 31 mai`() {
        val testDato = LocalDate.of(2023, 5, 30)
        val forventet = listOf(
            InntektsopplysningerDokumentType.REGNSKAP_FORRIGE_AAR,
            InntektsopplysningerDokumentType.SKATTEMELDING_OPTIONAL,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON_OPTIONAL
        )
        assertEquals(forventet, dokumenterSomSkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding og næringsSpesifikasjon etter 31 mai og før siste tertial`() {
        val testDato = LocalDate.of(2023, 6, 1)
        val forventet = listOf(
            InntektsopplysningerDokumentType.SKATTEMELDING,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON
        )
        assertEquals(forventet, dokumenterSomSkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding, næringsSpesifikasjon og foreløpig regnskap i siste tertial`() {
        val testDato = LocalDate.of(2023, 9, 1)
        val forventet = listOf(
            InntektsopplysningerDokumentType.SKATTEMELDING,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON,
            InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG
        )
        assertEquals(forventet, dokumenterSomSkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding, næringsSpesifikasjon og foreløpig regnskap etter siste tertial`() {
        val testDato = LocalDate.of(2023, 12, 31)
        val forventet = listOf(
            InntektsopplysningerDokumentType.SKATTEMELDING,
            InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON,
            InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG
        )
        assertEquals(forventet, dokumenterSomSkalSendes(testDato))
    }
}
