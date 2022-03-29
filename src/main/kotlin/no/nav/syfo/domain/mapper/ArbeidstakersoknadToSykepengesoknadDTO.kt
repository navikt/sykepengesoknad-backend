package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.sporsmalprossesering.*
import no.nav.syfo.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato

fun konverterArbeidstakersoknadTilSykepengesoknadDTO(
    sykepengesoknad: Sykepengesoknad,
    mottaker: Mottaker?,
    erEttersending: Boolean,
    soknadsperioder: List<SoknadsperiodeDTO>,
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
        arbeidsgiverForskutterer = sykepengesoknad.arbeidsgiverForskutterer?.tilArbeidsgiverForskuttererDTO(),
        fom = sykepengesoknad.fom,
        tom = sykepengesoknad.tom,
        startSyketilfelle = sykepengesoknad.startSykeforlop,
        arbeidGjenopptatt = arbeidGjenopptattDato(sykepengesoknad),
        sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet,
        opprettet = sykepengesoknad.opprettet,
        sendtNav = sykepengesoknad.sendtNav,
        sendtArbeidsgiver = sykepengesoknad.sendtArbeidsgiver,
        egenmeldinger = hentEgenmeldinger(sykepengesoknad),
        fravarForSykmeldingen = hentFravarForSykmeldingen(sykepengesoknad),
        papirsykmeldinger = hentPapirsykmeldinger(sykepengesoknad),
        fravar = samleFravaerListe(sykepengesoknad),
        andreInntektskilder = hentInntektListeArbeidstaker(sykepengesoknad),
        soknadsperioder = soknadsperioder,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        ettersending = erEttersending,
        mottaker = mottaker?.tilMottakerDTO(),
        egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
        permitteringer = sykepengesoknad.hentPermitteringer(),
        merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
    )
}
