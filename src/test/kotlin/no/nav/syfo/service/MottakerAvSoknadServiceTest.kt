package no.nav.syfo.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.syfo.client.flexsyketilfelle.FlexSyketilfelleClient
import no.nav.syfo.client.narmesteleder.Forskuttering
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.domain.*
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.juridiskvurdering.JuridiskVurderingKafkaProducer
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.util.Metrikk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate.now
import java.time.LocalDateTime
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
    private lateinit var narmesteLederClient: NarmesteLederClient

    @Mock
    private lateinit var identService: IdentService

    @Mock
    private lateinit var metrikk: Metrikk

    private val folkeregisterIdenter = FolkeregisterIdenter("fnr", emptyList())

    @Mock
    private lateinit var juridiskVurderingKafkaProducer: JuridiskVurderingKafkaProducer

    @Test
    fun soknadForArbeidstakerErInnenforArbeidsgiverperioden_soknadSendesTilArbeidsgiver() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(16),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforArbeidsgiverperioden_soknadSendesTilArbeidsgiverNullTilfelle() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any())).thenReturn(null)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(16),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforOgUtenforArbeidsgiverperiodenOgIkkeOppbrukt_soknadSendesTilArbeidsgiver() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErInnenforOgUtenforArbeidsgiverperioden_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now(),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgNLAgForskutterer_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.JA)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )
        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgNLAgForskuttererIkke_soknadSendesTilNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.NEI)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgForskutterer_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.UKJENT)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgUkjentForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn(Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.UKJENT)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )
        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgIngenSvarForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.UKJENT)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
        )

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErUtenforArbeidsgiverperiodenOgSpsAgIkkeSpurtForskuttering_soknadSendesTilArbeidsgiverOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.UKJENT)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE
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
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )
        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.ARBEIDSGIVER_OG_NAV))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringOverlapperPeriodeSendesTilAgOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(17),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.NAV))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringUtenforPeriodeSendesTilNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.NEI)

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.NAV))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilNav_korrigeringInnenforPeriodeSendesTilAgOgNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(16),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.NAV))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringOverlapperPeriodeSendesTilAgOgNAV() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(17),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.ARBEIDSGIVER))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER_OG_NAV)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringInnenforPeriodeSendesTilAg() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, false, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(0),
            tom = now().plusDays(16),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.ARBEIDSGIVER))

        val mottaker = soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter)

        assertThat(mottaker).isEqualByComparingTo(Mottaker.ARBEIDSGIVER)
    }

    @Test
    fun soknadForArbeidstakerErSendtTilAg_korrigeringUtenforforPeriodeSendesTilAgOgNav() {
        whenever(flexSyketilfelleClient.beregnArbeidsgiverperiode(any(), any(), any()))
            .thenReturn((Arbeidsgiverperiode(0, true, Periode(now(), now().plusDays(16)))))

        val soknad = Sykepengesoknad(
            id = "sykepengesoknadId",
            fnr = "fnr",
            sykmeldingId = "sykmeldingId",
            status = NY,
            fom = now().plusDays(17),
            tom = now().plusDays(20),
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(narmesteLederClient.arbeidsgiverForskutterer(any(), any())).thenReturn(Forskuttering.NEI)

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.of(Mottaker.ARBEIDSGIVER))

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
            opprettet = LocalDateTime.now(),
            startSykeforlop = now(),
            sykmeldingSkrevet = LocalDateTime.now(),
            arbeidsgiverOrgnummer = "orgnummer",
            arbeidsgiverNavn = "arbNavn",
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            soknadPerioder = emptyList(),
            sporsmal = emptyList(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            korrigerer = "korrigertSoknad"
        )

        whenever(sykepengesoknadDAO.finnMottakerAvSoknad("korrigertSoknad"))
            .thenReturn(Optional.empty())

        assertThatThrownBy { soknadService.finnMottakerAvSoknad(soknad, folkeregisterIdenter) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Finner ikke mottaker for en korrigert søknad")
    }

    @Test
    fun arbeidsgiverForskuttererHenterIkkeForskutteringHvisOrgnummerMangler() {
        soknadService.arbeidsgiverForskutterer("", "fnr")

        verify(narmesteLederClient, never()).arbeidsgiverForskutterer(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()
        )
    }
}
