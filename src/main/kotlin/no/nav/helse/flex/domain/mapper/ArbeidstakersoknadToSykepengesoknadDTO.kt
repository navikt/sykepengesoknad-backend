package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.*
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.tilOsloLocalDateTime

fun konverterArbeidstakersoknadTilSykepengesoknadDTO(
    sykepengesoknad: Sykepengesoknad,
    mottaker: Mottaker?,
    erEttersending: Boolean,
    soknadsperioder: List<SoknadsperiodeDTO>
): SykepengesoknadDTO {
    return SykepengesoknadDTO(
        id = sykepengesoknad.id,
        type = sykepengesoknad.soknadstype.tilSoknadstypeDTO(),
        status = sykepengesoknad.status.tilSoknadstatusDTO(),
        fnr = sykepengesoknad.fnr,
        sykmeldingId = sykepengesoknad.sykmeldingId,
        arbeidsgiver = ArbeidsgiverDTO(
            sykepengesoknad.arbeidsgiverNavn,
            sykepengesoknad.arbeidsgiverOrgnummer
        ),
        arbeidssituasjon = sykepengesoknad.arbeidssituasjon?.tilArbeidssituasjonDTO(),
        korrigerer = sykepengesoknad.korrigerer,
        korrigertAv = sykepengesoknad.korrigertAv,
        soktUtenlandsopphold = harSoktSykepengerUnderUtlandsopphold(sykepengesoknad),
        arbeidsgiverForskutterer = null,
        fom = sykepengesoknad.fom,
        tom = sykepengesoknad.tom,
        startSyketilfelle = sykepengesoknad.startSykeforlop,
        arbeidGjenopptatt = arbeidGjenopptattDato(sykepengesoknad),
        sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet?.tilOsloLocalDateTime(),
        opprettet = sykepengesoknad.opprettet?.tilOsloLocalDateTime(),
        sendtNav = sykepengesoknad.sendtNav?.tilOsloLocalDateTime(),
        sendtArbeidsgiver = sykepengesoknad.sendtArbeidsgiver?.tilOsloLocalDateTime(),
        egenmeldinger = hentEgenmeldinger(sykepengesoknad),
        fravarForSykmeldingen = hentFravarForSykmeldingen(sykepengesoknad),
        papirsykmeldinger = hentPapirsykmeldinger(sykepengesoknad),
        fravar = samleFravaerListe(sykepengesoknad),
        andreInntektskilder = hentInntektListe(sykepengesoknad),
        soknadsperioder = soknadsperioder,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        ettersending = erEttersending,
        mottaker = mottaker?.tilMottakerDTO(),
        egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
        permitteringer = sykepengesoknad.hentPermitteringer(),
        merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
        utenlandskSykmelding = sykepengesoknad.utenlandskSykmelding,
        yrkesskade = sykepengesoknad.hentYrkesskade()

    )
}
