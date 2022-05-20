package no.nav.syfo.mock

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Soknadstatus.NY
import no.nav.syfo.domain.Soknadstatus.SENDT
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.testutil.besvarsporsmal
import no.nav.syfo.util.tilOsloInstant
import java.time.LocalDate.now
import java.time.LocalDateTime

fun opprettNySoknad(): Sykepengesoknad {
    val soknadMetadata = SoknadMetadata(
        fnr = "fnr",
        status = NY,
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

    return settOppSoknadArbeidsledig(soknadMetadata, false)
}

fun opprettSendtSoknadForArbeidsledige(
    besvarPermittert: Boolean = true
): Sykepengesoknad {
    var soknad = opprettNySoknad().copy(status = SENDT)

    soknad = soknad.besvarsporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
    if (besvarPermittert) {
        soknad = soknad.besvarsporsmal(tag = PERMITTERT_NAA, svar = "NEI")
        soknad = soknad.besvarsporsmal(tag = PERMITTERT_PERIODE, svar = "NEI")
    }
    soknad = soknad.besvarsporsmal(tag = FRISKMELDT, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = UTDANNING, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = ARBEIDSLEDIG_UTLAND, svar = "NEI")
    soknad = soknad.besvarsporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")

    return soknad
}
