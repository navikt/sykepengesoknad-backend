package no.nav.syfo.service

import com.nhaarman.mockitokotlin2.*
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Soknadstatus.KORRIGERT
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.syfo.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.Metrikk
import no.nav.syfo.util.tilOsloInstant
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class EttersendingSoknadServiceTest {

    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var soknadProducer: SoknadProducer

    @Mock
    private lateinit var metrikk: Metrikk

    @InjectMocks
    private lateinit var ettersendingSoknadService: EttersendingSoknadService

    @Test
    fun ettersendTilNavOppdatererSendtNavOgSender() {
        val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(
            id = "behandlingsdagerId",
            sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant()
        )
        val soknadArbeidstaker =
            arbeidstakereSoknadMock().copy(id = "arbeidstakerId", sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant())

        whenever(sykepengesoknadDAO.finnSykepengesoknad("behandlingsdagerId")).thenReturn(soknadBehandlingsdager)
        whenever(sykepengesoknadDAO.finnSykepengesoknad("arbeidstakerId")).thenReturn(soknadArbeidstaker)

        ettersendingSoknadService.ettersendTilNav(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilNav(soknadArbeidstaker)

        verify(sykepengesoknadDAO, never()).settSendtAg(anyString(), any())
        verify(sykepengesoknadDAO).settSendtNav(eq("behandlingsdagerId"), any())
        verify(sykepengesoknadDAO).settSendtNav(eq("arbeidstakerId"), any())
        verify(soknadProducer, times(2)).soknadEvent(any(), eq(Mottaker.ARBEIDSGIVER_OG_NAV), eq(true), eq(null))
    }

    @Test
    fun ettersendTilNAVKasterFeilHvisSoknadIkkeErSendt() {
        assertThrows(IllegalArgumentException::class.java) {
            val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(status = KORRIGERT)
            val soknadArbeidstaker = arbeidstakereSoknadMock().copy(status = KORRIGERT)

            try {
                ettersendingSoknadService.ettersendTilNav(soknadBehandlingsdager)
                Assertions.fail("Forventer exception")
            } catch (e: Exception) {
                try {
                    ettersendingSoknadService.ettersendTilNav(soknadArbeidstaker)
                    Assertions.fail("Forventer exception")
                } catch (e: Exception) {
                    verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
                    verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null))
                    throw e
                }
            }
        }
    }

    @Test
    fun ettersendTilNAVGjorIngentingHvisSoknadAlleredeErSendtTilNav() {
        val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant())
        val soknadArbeidstaker = arbeidstakereSoknadMock().copy(sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant())

        ettersendingSoknadService.ettersendTilNav(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilNav(soknadArbeidstaker)

        verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
        verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null))
    }

    @Test
    fun ettersendTilNAVKasterFeilHvisSoknadIkkeErSendtTilArbeidsgiver() {

        assertThrows(IllegalArgumentException::class.java) {
            val soknadBehandlingsdager = behandlingsdagerSoknadMock()
            val soknadArbeidstaker = arbeidstakereSoknadMock()

            try {
                ettersendingSoknadService.ettersendTilNav(soknadBehandlingsdager)
                Assertions.fail("Forventer exception")
            } catch (e: Exception) {
                try {
                    ettersendingSoknadService.ettersendTilNav(soknadArbeidstaker)
                    Assertions.fail("Forventer exception")
                } catch (e: Exception) {
                    verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
                    verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null))
                    throw e
                }
            }
        }
    }

    @Test
    fun ettersendTilArbeidsgiverOppdatererSendtAGOgSender() {
        val soknadBehandlingsdager =
            behandlingsdagerSoknadMock().copy(id = "behandlingsdagerId", sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant())
        val soknadArbeidstaker =
            arbeidstakereSoknadMock().copy(id = "arbeidstakerId", sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant())

        whenever(sykepengesoknadDAO.finnSykepengesoknad("behandlingsdagerId")).thenReturn(soknadBehandlingsdager)
        whenever(sykepengesoknadDAO.finnSykepengesoknad("arbeidstakerId")).thenReturn(soknadBehandlingsdager)

        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)

        verify(sykepengesoknadDAO).settSendtAg(eq("behandlingsdagerId"), any())
        verify(sykepengesoknadDAO).settSendtAg(eq("arbeidstakerId"), any())
        verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
        verify(soknadProducer, times(2)).soknadEvent(any(), eq(Mottaker.ARBEIDSGIVER), eq(true), eq(null))
    }

    @Test
    fun ettersendTilArbeidsgiverKasterFeilHvisSoknadIkkeErSendt() {

        assertThrows(IllegalArgumentException::class.java) {
            val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(status = KORRIGERT)
            val soknadArbeidstaker = arbeidstakereSoknadMock().copy(status = KORRIGERT)

            try {
                ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
                Assertions.fail("Forventer exception")
            } catch (e: Exception) {
                try {
                    ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)
                    Assertions.fail("Forventer exception")
                } catch (e: Exception) {
                    verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
                    verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null))
                    throw e
                }
            }
        }
    }

    @Test
    fun ettersendTilArbeidsgiverGjorIngentingHvisSoknadAlleredeErSendtTilArbeidsgiver() {
        val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(
            id = "arbeidstakerId",
            sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant()
        )
        val soknadArbeidstaker =
            arbeidstakereSoknadMock().copy(id = "arbeidstakerId", sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant())

        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)

        verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
        verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null))
    }

    private fun behandlingsdagerSoknadMock() = settOppSykepengesoknadBehandlingsdager(
        SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidssituasjon = ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(10),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            fnr = "fnr",
            fom = LocalDate.now().minusDays(20),
            tom = LocalDate.now().minusDays(10),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            status = SENDT,
            sykmeldingId = "sykmeldingId"
        ),
        true, LocalDate.now()
    )

    private fun arbeidstakereSoknadMock() = settOppSoknadArbeidstaker(
        SoknadMetadata(
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "Bedrift AS",
            startSykeforlop = LocalDate.now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidssituasjon = ARBEIDSTAKER,
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(10),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            fnr = "fnr",
            fom = LocalDate.now().minusDays(20),
            tom = LocalDate.now().minusDays(10),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            status = SENDT,
            sykmeldingId = "sykmeldingId"
        ),
        true, LocalDate.now()
    )
}
