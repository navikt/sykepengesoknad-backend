package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.genererSykepengesoknadFraMetadata
import no.nav.helse.flex.soknadsopprettelse.settOppSoknadAnnetArbeidsforhold
import no.nav.helse.flex.soknadsopprettelse.tilSoknadsperioder
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.LocalDate.now
import java.time.LocalDateTime

fun opprettNySoknadAnnet(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr",
        startSykeforlop = now().minusDays(24),
        fom = now().minusDays(19),
        tom = now().minusDays(10),
        soknadstype = Soknadstype.ANNET_ARBEIDSFORHOLD,
        arbeidssituasjon = Arbeidssituasjon.ANNET,
        arbeidsgiverNavn = null,
        arbeidsgiverOrgnummer = null,
        sykmeldingId = "289148ba-4c3c-4b3f-b7a3-209jf32f",
        sykmeldingSkrevet = LocalDateTime.now().minusDays(19).tilOsloInstant(),
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeAGDTO(
                fom = now().minusDays(19),
                tom = now().minusDays(10),
                gradert = GradertDTO(grad = 100, reisetilskudd = false),
                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = null),
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                reisetilskudd = false,
            )
        ).tilSoknadsperioder(),

    )

    return genererSykepengesoknadFraMetadata(soknadMetadata).copy(sporsmal = settOppSoknadAnnetArbeidsforhold(soknadMetadata, false))
}
