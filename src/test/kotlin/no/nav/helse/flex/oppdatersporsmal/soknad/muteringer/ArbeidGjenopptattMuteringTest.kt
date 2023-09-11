package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.SettOppSoknadOpts
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

class ArbeidGjenopptattMuteringTest {

    @Test
    fun `spørsmål i søknaden gjenoppstår hvis de av en eller annen grunn mangla`() {
        val fom = LocalDate.now().minusDays(19)
        val soknad = Sykepengesoknad(
            fnr = "1234",
            startSykeforlop = LocalDate.now().minusDays(24),
            fom = fom,
            tom = LocalDate.now().minusDays(10),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
            soknadPerioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = fom,
                    tom = LocalDate.now().minusDays(15),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false
                )
            ).tilSoknadsperioder(),
            egenmeldtSykmelding = null,
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false
        )
        val standardSoknad = soknad.copy(
            sporsmal = settOppSoknadArbeidstaker(
                opts = SettOppSoknadOpts(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = true,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = YrkesskadeSporsmalGrunnlag()
                ),

                andreKjenteArbeidsforhold = emptyList()

            )
        )

        val soknadUtenUtdanning = standardSoknad
            .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
            .fjernSporsmal("UTLAND_V2")

        soknadUtenUtdanning.sporsmal.find { it.tag == UTLAND_V2 }.`should be null`()
        soknadUtenUtdanning.sporsmal.shouldHaveSize(9)

        val mutertSoknad = soknadUtenUtdanning.arbeidGjenopptattMutering()

        mutertSoknad.sporsmal.find { it.tag == UTLAND_V2 }.`should not be null`()
        mutertSoknad.sporsmal.shouldHaveSize(10)
    }

    @Test
    fun `mutering av spørsmål om perioder som kommer etter svar på tilbake i arbeid`() {
        val basisdato = LocalDate.now()
        val soknad = Sykepengesoknad(
            fnr = "1234",
            startSykeforlop = basisdato,
            fom = basisdato,
            tom = basisdato.plusDays(10),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            sykmeldingSkrevet = basisdato.atStartOfDay().tilOsloInstant(),
            soknadPerioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = basisdato,
                    tom = basisdato.plusDays(4),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false
                ),
                SykmeldingsperiodeAGDTO(
                    fom = basisdato.plusDays(5),
                    tom = basisdato.plusDays(10),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false
                )
            ).tilSoknadsperioder(),
            egenmeldtSykmelding = null,
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false
        )
        val standardSoknad = soknad.copy(
            sporsmal = settOppSoknadArbeidstaker(
                SettOppSoknadOpts(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = true,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = YrkesskadeSporsmalGrunnlag()
                ),
                emptyList()
            )

        )

        standardSoknad.sporsmal.shouldHaveSize(11)
        standardSoknad.sporsmal.find { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_1" }.`should not be null`()

        val mutertSoknadUtenSpm = standardSoknad
            .besvarsporsmal(TILBAKE_I_ARBEID, svar = "JA")
            .besvarsporsmal(TILBAKE_NAR, svar = basisdato.plusDays(4).format(ISO_LOCAL_DATE))
            .arbeidGjenopptattMutering()

        mutertSoknadUtenSpm.sporsmal.shouldHaveSize(10)
        mutertSoknadUtenSpm.sporsmal.find { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_1" }.`should be null`()

        val mutertSoknadMedSpm = mutertSoknadUtenSpm
            .besvarsporsmal(TILBAKE_I_ARBEID, svar = "NEI")
            .arbeidGjenopptattMutering()

        mutertSoknadMedSpm.sporsmal.shouldHaveSize(11)
        mutertSoknadMedSpm.sporsmal.find { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_1" }.`should not be null`()
    }

    @Test
    fun `en liten tekstlig endring i et spørsmål gjør ikke at det byttes ut`() {
        val fom = LocalDate.now().minusDays(19)
        val soknad = Sykepengesoknad(
            fnr = "1234",
            startSykeforlop = LocalDate.now().minusDays(24),
            fom = fom,
            tom = LocalDate.now().minusDays(10),
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
            arbeidsgiverOrgnummer = "123456789",
            arbeidsgiverNavn = "ARBEIDSGIVER A/S",
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            soknadstype = Soknadstype.ARBEIDSTAKERE,
            sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
            soknadPerioder = listOf(
                SykmeldingsperiodeAGDTO(
                    fom = fom,
                    tom = LocalDate.now().minusDays(15),
                    gradert = GradertDTO(grad = 100, reisetilskudd = false),
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                    behandlingsdager = null,
                    innspillTilArbeidsgiver = null,
                    reisetilskudd = false
                )
            ).tilSoknadsperioder(),
            egenmeldtSykmelding = null,
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false
        )
        val standardSoknad = soknad.copy(
            sporsmal = settOppSoknadArbeidstaker(
                opts = SettOppSoknadOpts(
                    sykepengesoknad = soknad,
                    erForsteSoknadISykeforlop = true,
                    harTidligereUtenlandskSpm = false,
                    yrkesskade = YrkesskadeSporsmalGrunnlag()
                ),
                andreKjenteArbeidsforhold = emptyList()
            )

        )

        val spm = standardSoknad.sporsmal.find { it.tag == PERMISJON_V2 }!!.copy(sporsmalstekst = "Var De i permisjon?")

        val soknadMedEgenPermisjonSpmTekst = standardSoknad
            .replaceSporsmal(spm)

        soknadMedEgenPermisjonSpmTekst `should be equal to` soknadMedEgenPermisjonSpmTekst.arbeidGjenopptattMutering()
    }
}
