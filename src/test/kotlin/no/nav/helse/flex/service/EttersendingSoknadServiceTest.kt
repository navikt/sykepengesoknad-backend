package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.*
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus.KORRIGERT
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.SettOppSoknadOptions
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.settOppSykepengesoknadBehandlingsdager
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
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
import java.util.*

@ExtendWith(MockitoExtension::class)
class EttersendingSoknadServiceTest {
    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var soknadProducer: SoknadProducer

    @InjectMocks
    private lateinit var ettersendingSoknadService: EttersendingSoknadService

    private val fnr = "11111111111"

    @Test
    fun ettersendTilArbeidsgiverOppdatererSendtAGOgSender() {
        val soknadBehandlingsdager =
            behandlingsdagerSoknadMock().copy(
                id = "behandlingsdagerId",
                sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant(),
                status = SENDT,
            )
        val soknadArbeidstaker =
            arbeidstakereSoknadMock().copy(
                id = "arbeidstakerId",
                sendtNav = LocalDateTime.now().minusHours(4).tilOsloInstant(),
                status = SENDT,
            )

        whenever(sykepengesoknadDAO.finnSykepengesoknad("behandlingsdagerId")).thenReturn(soknadBehandlingsdager)
        whenever(sykepengesoknadDAO.finnSykepengesoknad("arbeidstakerId")).thenReturn(soknadBehandlingsdager)

        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)

        verify(sykepengesoknadDAO).settSendtAg(eq("behandlingsdagerId"), any())
        verify(sykepengesoknadDAO).settSendtAg(eq("arbeidstakerId"), any())
        verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
        verify(soknadProducer, times(2)).soknadEvent(any(), eq(Mottaker.ARBEIDSGIVER), eq(true), eq(null), eq(null))
    }

    @Test
    fun ettersendTilArbeidsgiverKasterFeilHvisSoknadIkkeErSendt() {
        assertThrows(IllegalArgumentException::class.java) {
            val soknadBehandlingsdager = behandlingsdagerSoknadMock().copy(status = KORRIGERT)
            val soknadArbeidstaker = arbeidstakereSoknadMock().copy(status = KORRIGERT)

            try {
                ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
                fail("Forventer exception")
            } catch (e: Exception) {
                try {
                    ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)
                    fail("Forventer exception")
                } catch (e: Exception) {
                    verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
                    verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null), eq(null))
                    throw e
                }
            }
        }
    }

    @Test
    fun ettersendTilArbeidsgiverGjorIngentingHvisSoknadAlleredeErSendtTilArbeidsgiver() {
        val soknadBehandlingsdager =
            behandlingsdagerSoknadMock().copy(
                id = "arbeidstakerId",
                sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant(),
                status = SENDT,
            )
        val soknadArbeidstaker =
            arbeidstakereSoknadMock().copy(
                id = "arbeidstakerId",
                sendtArbeidsgiver = LocalDateTime.now().minusHours(4).tilOsloInstant(),
                status = SENDT,
            )

        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadBehandlingsdager)
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadArbeidstaker)

        verify(sykepengesoknadDAO, never()).settSendtNav(anyString(), any())
        verify(soknadProducer, never()).soknadEvent(any(), any(), anyBoolean(), eq(null), eq(null))
    }

    private fun behandlingsdagerSoknadMock(): Sykepengesoknad {
        val soknadMetadata =
            Sykepengesoknad(
                arbeidsgiverOrgnummer = "123456789",
                arbeidsgiverNavn = "Bedrift AS",
                startSykeforlop = LocalDate.now(),
                sykmeldingSkrevet = Instant.now(),
                arbeidssituasjon = ARBEIDSTAKER,
                soknadPerioder =
                    listOf(
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
                fnr = fnr,
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now().minusDays(10),
                soknadstype = Soknadstype.ARBEIDSTAKERE,
                sykmeldingId = "sykmeldingId",
                id = UUID.randomUUID().toString(),
                status = NY,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                egenmeldingsdagerFraSykmelding = null,
                forstegangssoknad = false,
            )
        return soknadMetadata.copy(
            sporsmal =
                settOppSykepengesoknadBehandlingsdager(
                    SettOppSoknadOptions(
                        sykepengesoknad = soknadMetadata,
                        erForsteSoknadISykeforlop = true,
                        harTidligereUtenlandskSpm = false,
                    ),
                ),
        )
    }

    private fun arbeidstakereSoknadMock(): Sykepengesoknad {
        val soknadMetadata =
            Sykepengesoknad(
                arbeidsgiverOrgnummer = "123456789",
                arbeidsgiverNavn = "Bedrift AS",
                startSykeforlop = LocalDate.now(),
                sykmeldingSkrevet = Instant.now(),
                arbeidssituasjon = ARBEIDSTAKER,
                soknadPerioder =
                    listOf(
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
                fnr = fnr,
                fom = LocalDate.now().minusDays(20),
                tom = LocalDate.now().minusDays(10),
                soknadstype = Soknadstype.ARBEIDSTAKERE,
                sykmeldingId = "sykmeldingId",
                id = UUID.randomUUID().toString(),
                status = NY,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                egenmeldingsdagerFraSykmelding = null,
                forstegangssoknad = false,
            )
        return soknadMetadata.copy(
            sporsmal =
                settOppSoknadArbeidstaker(
                    SettOppSoknadOptions(
                        sykepengesoknad = soknadMetadata,
                        erForsteSoknadISykeforlop = true,
                        harTidligereUtenlandskSpm = false,
                    ),
                    emptyList(),
                    YrkesskadeSporsmalGrunnlag(),
                ),
        )
    }
}
