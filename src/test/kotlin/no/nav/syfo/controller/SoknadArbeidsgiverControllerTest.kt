package no.nav.syfo.controller

import no.nav.syfo.BaseTestClass
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.client.narmesteleder.Tilgang
import no.nav.syfo.controller.domain.soknadarbeidsgiver.RSSoknadArbeidsgiverRespons
import no.nav.syfo.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.jwt
import no.nav.syfo.mock.gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal
import no.nav.syfo.mock.gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal
import no.nav.syfo.mock.opprettSendtSoknad
import no.nav.syfo.mockAktiveNarmesteledere
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.syfo.soknadsopprettelse.tilSoknadsperioder
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.tilOsloInstant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SoknadArbeidsgiverControllerTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @BeforeEach
    fun setUp() {
        databaseReset.resetDatabase()
        narmestelederMockRestServiceServer?.reset()
        evictAllCaches()
    }

    val fnr = "0987654321"

    fun hentSoknaderForArbeidsgiver(fnr: String): RSSoknadArbeidsgiverRespons {
        val result = mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/arbeidsgiver/soknader?orgnummer=123456789")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .header("Narmeste-Leder-Fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk).andReturn()
        return OBJECT_MAPPER.readValue(result.response.contentAsString, RSSoknadArbeidsgiverRespons::class.java)
    }

    @Test
    fun `henter alle statuser`() {

        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal())
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal())

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader).hasSize(2)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader.any { søknad -> søknad.status == RSSoknadstatus.SENDT }).isTrue()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader.any { søknad -> søknad.status == RSSoknadstatus.NY }).isTrue()
    }

    @Test
    fun `henter ikke reisetilskuddsøknad`() {

        sykepengesoknadDAO.lagreSykepengesoknad(opprettSendtSoknad().copy(soknadstype = Soknadstype.REISETILSKUDD))

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).isEmpty()
    }

    val meta = SoknadMetadata(
        fnr = "fnr-7454630",
        status = Soknadstatus.SENDT,
        startSykeforlop = now().minusMonths(1),
        fom = now().minusMonths(1),
        tom = now().minusMonths(1).plusDays(8),
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
        arbeidsgiverOrgnummer = "123456789",
        arbeidsgiverNavn = "ARBEIDSGIVER A/S",
        soknadstype = Soknadstype.ARBEIDSTAKERE,
        sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
        sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1),
                tom = now().minusMonths(1).plusDays(4),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
            SykmeldingsperiodeAGDTO(
                fom = now().minusMonths(1).plusDays(5),
                tom = now().minusMonths(1).plusDays(8),
                gradert = GradertDTO(grad = 40, reisetilskudd = false),
                type = PeriodetypeDTO.GRADERT,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            ),
        ).tilSoknadsperioder(),
    )

    @Test
    fun `henter opprettede søknader kun etter at tilgang er gitt`() {
        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusWeeks(2),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta))
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettNySoknadMedFeriesporsmalSomUndersporsmal())

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].status).isEqualTo(RSSoknadstatus.NY)
    }

    @Test
    fun `henter ikke søknad som kun er sendt NAV`() {
        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusYears(2),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val soknad = gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta)
            .copy(sendtArbeidsgiver = null, sendtNav = Instant.now())
        sykepengesoknadDAO.lagreSykepengesoknad(soknad)

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(0)
    }

    @Test
    fun `henter ikke søknader fra andre ressurser`() {

        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal())
        val meta = SoknadMetadata(
            fnr = "fnr-7454631",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta))

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader.size).isEqualTo(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].status).isEqualTo(RSSoknadstatus.SENDT)
    }

    @Test
    fun `henter ikke søknader fra andre arbeidsgivere`() {

        val meta = SoknadMetadata(
            fnr = "fnr-7454630",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "987654321",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal())
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta))

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].status).isEqualTo(RSSoknadstatus.SENDT)
    }

    @Test
    fun `skal ikke vise informasjon om ressurs dersom ressursen ikke har søknader`() {

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(*Tilgang.values()),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).isEmpty()
    }

    @Test
    fun `skal ikke vise informasjon om ressurs når man ikke har tilgang til søknader`() {

        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal())

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(Tilgang.SYKMELDING, Tilgang.MOTE, Tilgang.OPPFOLGINGSPLAN),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                )
            )
        )

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).isEmpty()
    }

    @Test
    fun `skal ha tilgang til en ressurs, men ikke for den andre`() {

        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal())
        val meta = SoknadMetadata(
            fnr = "fnr-7454631",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        val soknad2 =
            sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta)).id

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454631",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
                    skrivetilgang = true,
                    tilganger = listOf(
                        Tilgang.SYKMELDING,
                        Tilgang.MOTE,
                        Tilgang.OPPFOLGINGSPLAN,
                        Tilgang.SYKEPENGESOKNAD
                    ),
                    aktivTom = null,
                    narmesteLederEpost = "",
                    narmesteLederFnr = "",
                    narmesteLederId = UUID.randomUUID(),
                    narmesteLederTelefonnummer = "",
                    timestamp = OffsetDateTime.now()
                ),
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
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

        val rsSoknadArbeidsgiverRespons = hentSoknaderForArbeidsgiver(fnr)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].id).isEqualTo(soknad2)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].status).isEqualTo(RSSoknadstatus.SENDT)
    }

    @Test
    fun `henting av søknader for ressurser fra flere orgnummere`() {

        val meta1 = SoknadMetadata(
            fnr = "fnr-7454630",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        val meta2 = SoknadMetadata(
            id = UUID.randomUUID().toString(),
            fnr = "fnr-7454631",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "987654321",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        val soknad1 = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta1)
        ).id
        val soknad2 = sykepengesoknadDAO.lagreSykepengesoknad(
            gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta2)
        ).id

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(
                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
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
                    fnr = "fnr-7454631",
                    orgnummer = "987654321",
                    aktivFom = now().minusMonths(1),
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

        val result = mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/arbeidsgiver/soknader")
                    .header("Authorization", "Bearer ${jwt("0987654321")}")
                    .header("Narmeste-Leder-Fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk).andReturn()
        val rsSoknadArbeidsgiverRespons =
            OBJECT_MAPPER.readValue(result.response.contentAsString, RSSoknadArbeidsgiverRespons::class.java)
        assertThat(rsSoknadArbeidsgiverRespons.humanResources).isEmpty()
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere).hasSize(2)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].orgnummer).isEqualTo("123456789")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].id).isEqualTo(soknad1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader[0].status).isEqualTo(RSSoknadstatus.SENDT)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[1].orgnummer).isEqualTo("987654321")
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[1].soknader).hasSize(1)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[1].soknader[0].id).isEqualTo(soknad2)
        assertThat(rsSoknadArbeidsgiverRespons.narmesteLedere[1].soknader[0].status).isEqualTo(RSSoknadstatus.SENDT)
    }

    @Test
    fun `verfisier at info om andre arbeidsgivere og arbeid utenfor norge ikke er med på respons`() {

        val meta1 = SoknadMetadata(
            id = UUID.randomUUID().toString(),
            fnr = "fnr-7454630",
            status = Soknadstatus.SENDT,
            startSykeforlop = now().minusMonths(1),
            fom = now().minusMonths(1),
            tom = now().minusMonths(1).plusDays(8),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "14e78e84-50a5-45bb-9919-191c54f99691",
            sykmeldingSkrevet = LocalDateTime.now().minusMonths(1).tilOsloInstant(),
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1),
                    now().minusMonths(1).plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
                SykmeldingsperiodeAGDTO(
                    now().minusMonths(1).plusDays(5),
                    now().minusMonths(1).plusDays(8),
                    gradert = GradertDTO(grad = 40, reisetilskudd = false),
                    type = PeriodetypeDTO.GRADERT,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false,
                ),
            ).tilSoknadsperioder(),
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            egenmeldtSykmelding = null

        )
        sykepengesoknadDAO.lagreSykepengesoknad(gammeltFormatOpprettSendtSoknadMedFeriesporsmalSomUndersporsmal(meta1)).id

        mockAktiveNarmesteledere(
            fnr = fnr,
            narmestelederRelasjoner = listOf(

                NarmesteLederRelasjon(
                    fnr = "fnr-7454630",
                    orgnummer = "123456789",
                    aktivFom = now().minusMonths(1),
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
                    fnr = "aktorId-745463061",
                    orgnummer = "987654321",
                    aktivFom = now().minusMonths(1),
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

        val result = mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/arbeidsgiver/soknader")
                    .header("Authorization", "Bearer ${jwt("0987654321")}")
                    .header("Narmeste-Leder-Fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk).andReturn()
        val rsSoknadArbeidsgiverRespons =
            OBJECT_MAPPER.readValue(result.response.contentAsString, RSSoknadArbeidsgiverRespons::class.java)

        rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader.forEach {
            assertThat(it.sporsmal).filteredOn { t -> t.tag == ANDRE_INNTEKTSKILDER }.isEmpty()
        }
        rsSoknadArbeidsgiverRespons.narmesteLedere[0].soknader.forEach {
            assertThat(it.sporsmal).filteredOn { t -> t.tag == ARBEID_UTENFOR_NORGE }.isEmpty()
        }
    }
}
