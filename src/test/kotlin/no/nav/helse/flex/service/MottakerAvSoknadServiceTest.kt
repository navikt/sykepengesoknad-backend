package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.forskuttering.domain.Forskuttering
import no.nav.helse.flex.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.Metrikk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.time.LocalDate.now
import java.util.*

@ExtendWith(MockitoExtension::class)
class MottakerAvSoknadServiceTest {

    @Mock
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @InjectMocks
    private lateinit var soknadService: MottakerAvSoknadService

    @Mock
    private lateinit var flexSyketilfelleClient: FlexSyketilfelleClient

    @Mock
    private lateinit var identService: IdentService

    @Mock
    private lateinit var forskutteringRepository: ForskutteringRepository

    @Mock
    private lateinit var metrikk: Metrikk

    private val folkeregisterIdenter = FolkeregisterIdenter("fnr", emptyList())

    @Mock
    private lateinit var juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer

    private val forskutteringJa = Forskuttering(
        id = null,
        oppdatert = Instant.now(),
        timestamp = Instant.now(),
        narmesteLederId = UUID.randomUUID(),
        orgnummer = "org",
        brukerFnr = "fnr",
        aktivFom = now(),
        aktivTom = null,
        arbeidsgiverForskutterer = true
    )

    private val forskutteringNei = forskutteringJa.copy(arbeidsgiverForskutterer = false)

    @Test
    fun soknadForArbeidstakerErInnenforArbeidsgiverperioden_soknadSendesTilArbeidsgiver() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(16),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforArbeidsgiverperioden_soknadSendesTilArbeidsgiverNullTilfelle() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(16),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforOgUtenforArbeidsgiverperiodenOgIkkeOppbrukt_soknadSendesTilArbeidsgiver() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforOgUtenforArbeidsgiverperioden_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgNLAgForskutterer_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(forskutteringJa)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false

        )
        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgNLAgForskuttererIkke_soknadSendesTilNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(forskutteringNei)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgForskutterer_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgUkjentForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn(Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )
        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgIngenSvarForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgIkkeSpurtForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            utenlandskSykmelding = false
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAgOgNav_korrigeringSendesTilAgOgNAV() {
        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(16),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )
        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.ARBEIDSGIVER_OG_NAV)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringOverlapperPeriodeSendesTilAgOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(17),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.NAV)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringUtenforPeriodeSendesTilNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(forskutteringNei)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.NAV)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringInnenforPeriodeSendesTilAgOgNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(16),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.NAV)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringOverlapperPeriodeSendesTilAgOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(17),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.ARBEIDSGIVER)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringInnenforPeriodeSendesTilAg() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(16),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.ARBEIDSGIVER)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringUtenforforPeriodeSendesTilAgOgNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), isNull(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(forskutteringRepository.finnForskuttering(any(), any())).thenReturn(forskutteringNei)

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Mottaker.ARBEIDSGIVER)

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErIkkeSendt_korrigeringSkalFeile() {
        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(20),
            opprettet = Instant.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = Instant.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad",
            utenlandskSykmelding = false
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(null)

        assertThatThrownBy { soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Finner ikke mottaker for en korrigert søknad")
    }
}
