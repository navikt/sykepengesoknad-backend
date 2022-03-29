package no.nav.syfo.service

import com.nhaarman.mockitokotlin2.*
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.client.narmesteleder.Tilgang
import no.nav.syfo.mock.opprettSendtSoknad
import no.nav.syfo.repository.SykepengesoknadDAO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class SoknadArbeidsgiverServiceTest {
    @Mock
    private lateinit var identService: IdentService

    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var narmesteLederClient: NarmesteLederClient

    @InjectMocks
    private lateinit var soknadArbeidsgiverService: SoknadArbeidsgiverService

    @Test
    fun `kun arbeidsgiver med sykepengesøknad-tilgang er nærmeste leder`() {
        whenever(narmesteLederClient.hentRelasjonerForNarmesteleder(anyString()))
            .thenReturn(
                listOf(
                    NarmesteLederRelasjon(
                        fnr = "fnr1",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = listOf(
                            Tilgang.SYKMELDING,
                            Tilgang.SYKEPENGESOKNAD,
                            Tilgang.MOTE,
                            Tilgang.OPPFOLGINGSPLAN
                        ),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    ),
                    NarmesteLederRelasjon(
                        fnr = "fnr2",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = listOf(
                            Tilgang.SYKMELDING,
                            Tilgang.MOTE,
                            Tilgang.OPPFOLGINGSPLAN
                        ),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    )
                )
            )
        val sykepengesoknad =
            opprettSendtSoknad().copy(fnr = "fnr1", arbeidsgiverOrgnummer = "orgnummer")
        whenever(sykepengesoknadDAO.finnSykepengesoknaderForNl(anyString(), anyString(), any()))
            .thenReturn(listOf(sykepengesoknad))
        val (narmesteLedere) = soknadArbeidsgiverService.hentSoknader("fnr", "orgnummer")
        assertThat(narmesteLedere).hasSize(1)
        assertThat(narmesteLedere!![0].fnr).isEqualTo("fnr1")
        verify(sykepengesoknadDAO).finnSykepengesoknaderForNl("fnr1", "orgnummer", LocalDate.MIN)
    }

    @Test
    fun hentSoknader_ToBrukereEnMedSykepengesoknad() {
        whenever(narmesteLederClient.hentRelasjonerForNarmesteleder(anyString()))
            .thenReturn(
                listOf(
                    NarmesteLederRelasjon(
                        fnr = "fnr1",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = listOf(
                            Tilgang.SYKMELDING,
                            Tilgang.SYKEPENGESOKNAD,
                            Tilgang.MOTE,
                            Tilgang.OPPFOLGINGSPLAN
                        ),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    ),
                    NarmesteLederRelasjon(
                        fnr = "fnr2",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = listOf(
                            Tilgang.SYKMELDING,
                            Tilgang.SYKEPENGESOKNAD,
                            Tilgang.MOTE,
                            Tilgang.OPPFOLGINGSPLAN
                        ),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    )
                )
            )

        whenever(sykepengesoknadDAO.finnSykepengesoknaderForNl("fnr1", "orgnummer", LocalDate.MIN))
            .thenReturn(emptyList())
        val sykepengesoknad = opprettSendtSoknad().copy(fnr = "fnr2")
        whenever(sykepengesoknadDAO.finnSykepengesoknaderForNl("fnr2", "orgnummer", LocalDate.MIN))
            .thenReturn(listOf(sykepengesoknad))

        val (narmesteLedere) = soknadArbeidsgiverService.hentSoknader("fnr", "orgnummer")
        assertThat(narmesteLedere).hasSize(1)
        assertThat(narmesteLedere!![0].fnr).isEqualTo(sykepengesoknad.fnr)
        verify(sykepengesoknadDAO, times(2)).finnSykepengesoknaderForNl(
            startsWith("fnr"),
            eq("orgnummer"),
            eq(LocalDate.MIN)
        )
    }

    @Test
    fun hentSoknader_ToBrukereEnMedSykepengesoknadOgEnAnnenMedTilgangenSYKEPENGESOKNAD() {
        whenever(narmesteLederClient.hentRelasjonerForNarmesteleder(anyString()))
            .thenReturn(
                listOf(
                    NarmesteLederRelasjon(
                        fnr = "aktor1",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = listOf(
                            Tilgang.SYKMELDING,
                            Tilgang.SYKEPENGESOKNAD,
                            Tilgang.MOTE,
                            Tilgang.OPPFOLGINGSPLAN
                        ),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    ),
                    NarmesteLederRelasjon(
                        fnr = "aktor2",
                        orgnummer = "orgnummer",
                        aktivFom = LocalDate.MIN,
                        skrivetilgang = true,
                        tilganger = emptyList(),
                        aktivTom = null,
                        narmesteLederEpost = "",
                        narmesteLederFnr = "",
                        narmesteLederId = UUID.randomUUID(),
                        narmesteLederTelefonnummer = "",
                        timestamp = OffsetDateTime.now()
                    )

                )
            )
        whenever(sykepengesoknadDAO.finnSykepengesoknaderForNl(anyString(), anyString(), any()))
            .thenReturn(emptyList())
        val (narmesteLedere) = soknadArbeidsgiverService.hentSoknader("fnr", "orgnummer")
        assertThat(narmesteLedere).isEmpty()
        verify(sykepengesoknadDAO).finnSykepengesoknaderForNl("aktor1", "orgnummer", LocalDate.MIN)
    }
}
