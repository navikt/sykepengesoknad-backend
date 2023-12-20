package no.nav.helse.flex.inntektsopplysninger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HvilkeDokumenterTest {
    @Test
    fun `skal returnere regnskap for forrige år, skattemelding og næringsSpesifikasjon optional før 31 mai`() {
        val testDato = LocalDate.of(2023, 5, 30)
        val forventet = listOf(
            DokumentTyper.REGNSKAP_FORRIGE_AAR,
            DokumentTyper.SKATTEMELDING_OPTIONAL,
            DokumentTyper.NARINGSSPESIFIKASJON_OPTIONAL
        )
        assertEquals(forventet, dokumenterSomkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding og næringsSpesifikasjon etter 31 mai og før siste tertial`() {
        val testDato = LocalDate.of(2023, 6, 1)
        val forventet = listOf(
            DokumentTyper.SKATTEMELDING,
            DokumentTyper.NARINGSSPESIFIKASJON
        )
        assertEquals(forventet, dokumenterSomkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding, næringsSpesifikasjon og foreløpig regnskap i siste tertial`() {
        val testDato = LocalDate.of(2023, 9, 1)
        val forventet = listOf(
            DokumentTyper.SKATTEMELDING,
            DokumentTyper.NARINGSSPESIFIKASJON,
            DokumentTyper.REGNSKAP_FORELOPIG
        )
        assertEquals(forventet, dokumenterSomkalSendes(testDato))
    }

    @Test
    fun `skal returnere skattemelding, næringsSpesifikasjon og foreløpig regnskap etter siste tertial`() {
        val testDato = LocalDate.of(2023, 12, 31)
        val forventet = listOf(
            DokumentTyper.SKATTEMELDING,
            DokumentTyper.NARINGSSPESIFIKASJON,
            DokumentTyper.REGNSKAP_FORELOPIG
        )
        assertEquals(forventet, dokumenterSomkalSendes(testDato))
    }
}
