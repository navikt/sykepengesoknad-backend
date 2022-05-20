package no.nav.helse.flex.domain.mapper

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentArbeidUtenforNorge
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentBehandlingsdager
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentEgenmeldinger
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentInntektListeBehandlingsdager
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentPapirsykmeldinger
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentPermitteringer
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.tilOsloLocalDateTime

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
        opprettet = sykepengesoknad.opprettet?.tilOsloLocalDateTime(),
        sendtNav = sykepengesoknad.sendtNav?.tilOsloLocalDateTime(),
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        arbeidssituasjon = sykepengesoknad.arbeidssituasjon?.tilArbeidssituasjonDTO(),
        sendtArbeidsgiver = sykepengesoknad.sendtArbeidsgiver?.tilOsloLocalDateTime(),
        arbeidsgiver = hentArbeidsgiver(sykepengesoknad),
        arbeidsgiverForskutterer = null,
        ettersending = erEttersending,
        egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
        mottaker = mottaker?.tilMottakerDTO(),
        startSyketilfelle = sykepengesoknad.startSykeforlop!!,
        sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet?.tilOsloLocalDateTime(),
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
