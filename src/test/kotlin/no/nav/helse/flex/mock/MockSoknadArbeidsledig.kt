package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.oppsummering
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.Instant
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.util.*

fun opprettNySoknad(): Sykepengesoknad {
    val sykepengesoknad =
        Sykepengesoknad(
            fnr = "11111111111",
            startSykeforlop = now().minusDays(24),
            fom = now().minusDays(19),
            tom = now().minusDays(10),
            soknadstype = Soknadstype.ARBEIDSLEDIG,
            arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            arbeidsgiverNavn = null,
            arbeidsgiverOrgnummer = null,
            sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
            sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
            id = UUID.randomUUID().toString(),
            status = Soknadstatus.NY,
            opprettet = Instant.now(),
            sporsmal = emptyList(),
            soknadPerioder =
                listOf(
                    SykmeldingsperiodeAGDTO(
                        fom = now().minusDays(19),
                        tom = now().minusDays(15),
                        gradert = GradertDTO(grad = 100, reisetilskudd = false),
                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                    SykmeldingsperiodeAGDTO(
                        fom = now().minusDays(14),
                        tom = now().minusDays(10),
                        gradert = GradertDTO(grad = 40, reisetilskudd = false),
                        type = PeriodetypeDTO.GRADERT,
                        aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                        behandlingsdager = null,
                        innspillTilArbeidsgiver = null,
                        reisetilskudd = false,
                    ),
                ).tilSoknadsperioder(),
            utenlandskSykmelding = false,
            egenmeldingsdagerFraSykmelding = null,
            forstegangssoknad = false,
        )

    return sykepengesoknad.copy(
        sporsmal =
            settOppSoknadArbeidsledig(
                SettOppSoknadOptions(
                    sykepengesoknad,
                    false,
                    false,
                ),
                YrkesskadeSporsmalGrunnlag(),
            ),
    )
}

fun opprettSendtSoknadForArbeidsledige(): Sykepengesoknad {
    var soknad = opprettNySoknad().copy(status = SENDT)

    soknad = soknad.besvarsporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
    soknad = soknad.besvarsporsmal(tag = FRISKMELDT, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
    soknad = soknad.oppsummering()

    return soknad
}
