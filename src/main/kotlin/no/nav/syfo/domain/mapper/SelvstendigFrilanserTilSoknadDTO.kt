package no.nav.syfo.domain.mapper

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentArbeidUtenforNorge
import no.nav.syfo.domain.mapper.sporsmalprossesering.hentPermitteringer
import no.nav.syfo.util.tilOsloLocalDateTime

fun konverterSelvstendigOgFrilanserTilSoknadDTO(
    sykepengesoknad: Sykepengesoknad,
    soknadsperioder: List<SoknadsperiodeDTO>,
    harRedusertVenteperiode: Boolean,
): SykepengesoknadDTO {
    return SykepengesoknadDTO(
        id = sykepengesoknad.id,
        fnr = sykepengesoknad.fnr,
        sykmeldingId = sykepengesoknad.sykmeldingId,
        egenmeldtSykmelding = sykepengesoknad.egenmeldtSykmelding,
        type = sykepengesoknad.soknadstype.tilSoknadstypeDTO(),
        status = sykepengesoknad.status.tilSoknadstatusDTO(),
        fom = sykepengesoknad.fom,
        tom = sykepengesoknad.tom,
        opprettet = sykepengesoknad.opprettet?.tilOsloLocalDateTime(),
        sendtNav = sykepengesoknad.sendtNav?.tilOsloLocalDateTime(),
        startSyketilfelle = sykepengesoknad.startSykeforlop,
        sykmeldingSkrevet = sykepengesoknad.sykmeldingSkrevet?.tilOsloLocalDateTime(),
        arbeidsgiver = null,
        arbeidssituasjon = sykepengesoknad.arbeidssituasjon?.tilArbeidssituasjonDTO(),
        korrigerer = sykepengesoknad.korrigerer,
        korrigertAv = sykepengesoknad.korrigertAv,
        soknadsperioder = soknadsperioder,
        sporsmal = sykepengesoknad.sporsmal.map { it.tilSporsmalDTO() },
        avsendertype = sykepengesoknad.avsendertype?.tilAvsendertypeDTO(),
        harRedusertVenteperiode = harRedusertVenteperiode,
        permitteringer = sykepengesoknad.hentPermitteringer(),
        merknaderFraSykmelding = sykepengesoknad.merknaderFraSykmelding.tilMerknadDTO(),
        arbeidUtenforNorge = sykepengesoknad.hentArbeidUtenforNorge(),
    )
}
