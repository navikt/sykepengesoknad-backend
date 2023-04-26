package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.*
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.tilOsloLocalDateTime

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
        papirsykmeldinger = hentPapirsykmeldinger(sykepengesoknad),
        fravar = samleFravaerListe(sykepengesoknad),
        andreInntektskilder = hentInntektListe(sykepengesoknad),
        soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(sykepengesoknad).first,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        ettersending = erEttersending,
        mottaker = mottaker?.tilMottakerDTO(),
        permitteringer = sykepengesoknad.hentPermitteringer(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
        utenlandskSykmelding = sykepengesoknad.utenlandskSykmelding,
        yrkesskade = sykepengesoknad.hentYrkesskade(),
        egenmeldingsdagerFraSykmelding = sykepengesoknad.egenmeldingsdagerFraSykmelding.parseEgenmeldingsdagerFraSykmelding()

    )
}
