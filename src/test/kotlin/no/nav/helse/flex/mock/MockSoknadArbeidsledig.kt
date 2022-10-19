package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstatus.SENDT
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.UTDANNING
import no.nav.helse.flex.soknadsopprettelse.genererSykepengesoknadFraMetadata
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.LocalDate.now
import java.time.LocalDateTime

fun opprettNySoknad(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr",
        startSykeforlop = now().minusDays(24),
        fom = now().minusDays(19),
        tom = now().minusDays(10),
        soknadstype = Soknadstype.ARBEIDSLEDIG,
        arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
        arbeidsgiverNavn = null,
        arbeidsgiverOrgnummer = null,
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-385b7e7c927d",
        sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
        sykmeldingsperioder = listOf(
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

    )

    return genererSykepengesoknadFraMetadata(soknadMetadata).copy(sporsmal = settOppSoknadArbeidsledig(soknadMetadata, false))
}

fun opprettSendtSoknadForArbeidsledige(): Sykepengesoknad {
    var soknad = opprettNySoknad().copy(status = SENDT)

    soknad = soknad.besvarsporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
    soknad = soknad.besvarsporsmal(tag = FRISKMELDT, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = UTDANNING, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = ARBEIDSLEDIG_UTLAND, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")

    return soknad
}
