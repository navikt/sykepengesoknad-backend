package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.sporsmalprossesering.*
import no.nav.syfo.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato

fun konverterTilSykepengesoknadDTO(
    sykepengesoknad: Sykepengesoknad,
    mottaker: Mottaker?,
    erEttersending: Boolean
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
        papirsykmeldinger = hentPapirsykmeldinger(sykepengesoknad),
        fravar = samleFravaerListe(sykepengesoknad),
        andreInntektskilder = hentInntektListeArbeidstaker(sykepengesoknad),
        soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        ettersending = erEttersending,
        mottaker = mottaker?.tilMottakerDTO(),
        permitteringer = sykepengesoknad.hentPermitteringer(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
    )
}
