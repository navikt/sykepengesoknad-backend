package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Mottaker
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentArbeidUtenforNorge
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentBehandlingsdager
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentEgenmeldinger
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentInntektListeBehandlingsdager
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentPapirsykmeldinger
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentPermitteringer

fun konverterTilSykepengesoknadBehandlingsdagerDTO(
    sykepengesoknad: Sykepengesoknad,
    mottaker: Mottaker? = null,
    erEttersending: Boolean = false
): SykepengesoknadDTO {
    return SykepengesoknadDTO(
        id = sykepengesoknad.id,
        type = SoknadstypeDTO.BEHANDLINGSDAGER,
        status = sykepengesoknad.status.tilSoknadstatusDTO(),
        fnr = sykepengesoknad.fnr,
        korrigerer = sykepengesoknad.korrigerer,
        korrigertAv = sykepengesoknad.korrigertAv,
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        opprettet = sykepengesoknad.opprettet!!,
        sendtNav = sykepengesoknad.sendtNav,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        arbeidssituasjon = sykepengesoknad.arbeidssituasjon?.tilArbeidssituasjonDTO(),
        sendtArbeidsgiver = sykepengesoknad.sendtArbeidsgiver,
        arbeidsgiver = hentArbeidsgiver(sykepengesoknad),
        arbeidsgiverForskutterer = sykepengesoknad.arbeidsgiverForskutterer?.tilArbeidsgiverForskuttererDTO(),
        ettersending = erEttersending,
        egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
        mottaker = mottaker?.tilMottakerDTO(),
        startSyketilfelle = sykepengesoknad.startSykeforlop!!,
        sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet!!,
        fom = sykepengesoknad.fom!!,
        tom = sykepengesoknad.tom!!,
        sykmeldingId = sykepengesoknad.sykmeldingId!!,
        soknadsperioder = hentSoknadsPerioder(sykepengesoknad),
        andreInntektskilder = hentInntektListeBehandlingsdager(sykepengesoknad),
        egenmeldinger = hentEgenmeldinger(sykepengesoknad),
        papirsykmeldinger = hentPapirsykmeldinger(sykepengesoknad),
        behandlingsdager = sykepengesoknad.hentBehandlingsdager(),
        permitteringer = sykepengesoknad.hentPermitteringer(),
        merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
    )
}

private fun hentSoknadsPerioder(sykepengesoknad: Sykepengesoknad): List<SoknadsperiodeDTO> {
    return sykepengesoknad.soknadPerioder!!.map {
        SoknadsperiodeDTO(
            fom = it.fom,
            tom = it.tom,
            sykmeldingsgrad = it.grad,
            sykmeldingstype = it.sykmeldingstype?.tilSykmeldingstypeDTO()
        )
    }
}

private fun hentArbeidsgiver(soknad: Sykepengesoknad): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(
        soknad.arbeidsgiverNavn,
        soknad.arbeidsgiverOrgnummer
    )
}
