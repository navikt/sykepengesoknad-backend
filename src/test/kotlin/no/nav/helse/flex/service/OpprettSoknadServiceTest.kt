package no.nav.helse.flex.service

import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.julesoknad.LagreJulesoknadKandidater
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.soknadsopprettelse.hentTidligsteFomForSykmelding
import no.nav.helse.flex.util.Metrikk
import no.nav.helse.flex.util.tilOsloInstant
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
    private lateinit var lagreJulesoknadKandidater: LagreJulesoknadKandidater

    @Mock
    private lateinit var toggle: EnvironmentToggles

    @Mock
    private lateinit var metrikk: Metrikk

    @Mock
    private lateinit var slettSoknaderTilKorrigertSykmeldingService: SlettSoknaderTilKorrigertSykmeldingService

    @Mock
    private lateinit var aktiveringProducer: AktiveringProducer

    @InjectMocks
    private lateinit var opprettSoknadService: OpprettSoknadService

    @Test
    fun `test at vi finner den eldste fom i en sykmelding`() {
        val metadata = SoknadMetadata(
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
}
