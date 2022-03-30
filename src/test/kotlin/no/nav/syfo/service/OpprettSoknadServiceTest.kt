package no.nav.syfo.service

import no.nav.syfo.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.syfo.config.EnvironmentToggles
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.mock.opprettNySoknad
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.syfo.util.Metrikk
import no.nav.syfo.util.tilOsloInstant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections.emptyList

@ExtendWith(MockitoExtension::class)
class OpprettSoknadServiceTest {

    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Mock
    private lateinit var soknadProducer: SoknadProducer

    @Mock
    private lateinit var identService: IdentService

    @Mock
    private lateinit var flexSyketilfelleClient: FlexSyketilfelleClient

    @Mock
    private lateinit var julesoknadService: JulesoknadService

    @Mock
    private lateinit var toggle: EnvironmentToggles

    @Mock
    private lateinit var metrikk: Metrikk

    @Mock
    private lateinit var slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService

    @InjectMocks
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Test
    fun sykmeldtRegistrertSomBlittArbeidsledig() {
        val arbeidstakerSoknad = arbeidstakersoknad(LocalDate.of(2019, 9, 10), LocalDate.of(2019, 9, 17))
        val arbeidsledigSoknad = arbeidsledigsoknad("arbeidsledig", LocalDate.of(2019, 9, 27), LocalDate.of(2019, 9, 30))

        opprettSoknadService.metrikkBlittArbeidsledig(arbeidsledigSoknad, listOf(arbeidstakerSoknad, arbeidsledigSoknad))

        verify(metrikk, times(1)).tellBlittArbeidsledig()
    }

    @Test
    fun sykmeldtIkkeRegistrertSomArbeidsledigSidenForrigeSykeforlopErLengeSiden() {
        val arbeidstakerSoknad = arbeidstakersoknad(LocalDate.of(2019, 8, 10), LocalDate.of(2019, 8, 17))
        val arbeidsledigSoknad = arbeidsledigsoknad("arbeidsledig", LocalDate.of(2019, 9, 27), LocalDate.of(2019, 9, 30))

        opprettSoknadService.metrikkBlittArbeidsledig(arbeidsledigSoknad, listOf(arbeidstakerSoknad, arbeidsledigSoknad))

        verify(metrikk, never()).tellBlittArbeidsledig()
    }

    @Test
    fun sykmeldtIkkeRegistrertSomBlittArbeidsledigSidenAlleredeArbeidsledig() {
        val arbeidsledigSoknadTidligere = arbeidsledigsoknad("arbeidsledigTidligere", LocalDate.of(2019, 8, 10), LocalDate.of(2019, 8, 17))
        val arbeidsledigSoknad = arbeidsledigsoknad("arbeidsledig", LocalDate.of(2019, 9, 27), LocalDate.of(2019, 9, 30))

        opprettSoknadService.metrikkBlittArbeidsledig(arbeidsledigSoknad, listOf(arbeidsledigSoknadTidligere, arbeidsledigSoknad))

        verify(metrikk, never()).tellBlittArbeidsledig()
    }

    @Test
    fun sykmeldtRegistrertSomBlittArbeidsledig16DagerSidenForrigeSykmelding() {
        val arbeidsledigFom = LocalDate.of(2019, 9, 27)
        val arbeidstakerTom = arbeidsledigFom.minusDays(16)

        val arbeidstakerSoknad = arbeidstakersoknad(LocalDate.of(2019, 9, 10), arbeidstakerTom)
        val arbeidsledigSoknad = arbeidsledigsoknad("arbeidsledig", arbeidsledigFom, LocalDate.of(2019, 9, 30))

        opprettSoknadService.metrikkBlittArbeidsledig(arbeidsledigSoknad, listOf(arbeidsledigSoknad, arbeidstakerSoknad))

        verify(metrikk, times(1)).tellBlittArbeidsledig()
    }

    @Test
    fun `test at vi finner den eldste fom i en sykmelding`() {
        val metadata = SoknadMetadata(
            status = NY,
            fnr = "fnr",
            startSykeforlop = LocalDate.now(),
            fom = LocalDate.now().minusDays(34),
            tom = LocalDate.now(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            sykmeldingId = "id1",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(4).tilOsloInstant(),
            sykmeldingsperioder = emptyList()
        )

        val ingenEksisterendeSoknader = hentTidligsteFomForSykmelding(metadata, emptyList())

        assertThat(ingenEksisterendeSoknader).isEqualTo(metadata.fom)

        val eksisterende1 = opprettNySoknad().copy(sykmeldingId = metadata.sykmeldingId, fom = LocalDate.now().minusDays(993))
        val eksisterende2 = opprettNySoknad().copy(sykmeldingId = metadata.sykmeldingId, fom = LocalDate.now().minusDays(5000))
        val eksisterende3 = opprettNySoknad().copy(sykmeldingId = "id annen", fom = LocalDate.now().minusDays(6004))

        val medEksisterendeSoknader = hentTidligsteFomForSykmelding(metadata, listOf(eksisterende1, eksisterende2, eksisterende3).shuffled())
        assertThat(medEksisterendeSoknader).isEqualTo(eksisterende2.fom)
    }

    private fun arbeidstakersoknad(fom: LocalDate, tom: LocalDate): Sykepengesoknad {

        return Sykepengesoknad(
            id = "arbeidstaker",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = fom,
            tom = tom,
            opprettet = fom.atStartOfDay().tilOsloInstant(),
            startSykeforlop = fom,
            sykmeldingSkrevet = fom.atStartOfDay().tilOsloInstant(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = kotlin.collections.emptyList(),
            sporsmal = kotlin.collections.emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )
    }

    private fun arbeidsledigsoknad(id: String, fom: LocalDate, tom: LocalDate): Sykepengesoknad {

        return Sykepengesoknad(
            id = id,
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = fom,
            tom = tom,
            opprettet = fom.atStartOfDay().tilOsloInstant(),
            startSykeforlop = fom,
            sykmeldingSkrevet = fom.atStartOfDay().tilOsloInstant(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            soknadPerioder = kotlin.collections.emptyList(),
            sporsmal = kotlin.collections.emptyList(),
            soknadstype = Soknadstype.ARBEIDSLEDIG
        )
    }
}
