package no.nav.syfo.controller.mapper

import no.nav.syfo.controller.domain.sykepengesoknad.*
import no.nav.syfo.domain.*
import no.nav.syfo.util.EnumUtil
import java.util.Optional.ofNullable

private fun mapSvarTilRSSvar(svar: Svar): RSSvar {
    return RSSvar(
        id = svar.id,
        verdi = svar.verdi,
        avgittAv = EnumUtil.konverter(RSSvarAvgittAv::class.java, svar.avgittAv)
    )
}

private fun mapSoknadsperiode(soknadsperiode: Soknadsperiode): RSSoknadsperiode {
    return RSSoknadsperiode(
        fom = soknadsperiode.fom,
        tom = soknadsperiode.tom,
        grad = soknadsperiode.grad,
        sykmeldingstype = mapSykmeldingstype(soknadsperiode.sykmeldingstype)
    )
}

private fun mapSykmeldingstype(sykmeldingstype: Sykmeldingstype?): RSSykmeldingstype? {
    return if (sykmeldingstype == null) {
        null
    } else RSSykmeldingstype.valueOf(sykmeldingstype.name)
}

fun Sporsmal.mapSporsmalTilRs(): RSSporsmal {
    return RSSporsmal(
        id = this.id,
        tag = this.tag,
        sporsmalstekst = this.sporsmalstekst,
        undertekst = this.undertekst,
        svartype = EnumUtil.konverter(RSSvartype::class.java, this.svartype),
        min = this.min,
        max = this.max,
        pavirkerAndreSporsmal = this.pavirkerAndreSporsmal,
        kriterieForVisningAvUndersporsmal = EnumUtil.konverter(
            RSVisningskriterie::class.java,
            this.kriterieForVisningAvUndersporsmal
        ),
        svar = this.svar.map { mapSvarTilRSSvar(it) },
        undersporsmal = this.undersporsmal.map { it.mapSporsmalTilRs() }
    )
}

private fun map(arbeidsgiverNavn: String?, arbeidsgiverOrgnummer: String?): RSArbeidsgiver? {
    return if (arbeidsgiverNavn == null || arbeidsgiverOrgnummer == null)
        null
    else
        RSArbeidsgiver(
            navn = arbeidsgiverNavn,
            orgnummer = arbeidsgiverOrgnummer
        )
}

fun konverterSoknadstatus(soknadstatus: Soknadstatus): RSSoknadstatus {
    return if (soknadstatus === Soknadstatus.UTGATT) {
        RSSoknadstatus.UTGAATT
    } else EnumUtil.konverter(RSSoknadstatus::class.java, soknadstatus)
}

private fun Merknad.mapMerknad(): RSMerknad {
    return RSMerknad(
        type = this.type,
        beskrivelse = this.beskrivelse
    )
}

fun Sykepengesoknad.tilRSSykepengesoknad() = RSSykepengesoknad(
    id = this.id,
    soknadstype = EnumUtil.konverter(RSSoknadstype::class.java, this.soknadstype),
    status = konverterSoknadstatus(this.status),
    opprettetDato = this.opprettet?.toLocalDate(),
    avbruttDato = this.avbruttDato,
    innsendtDato = ofNullable(this.sendtNav).map { it.toLocalDate() }.orElse(null),
    sendtTilNAVDato = ofNullable(this.sendtNav).orElse(null),
    korrigerer = this.korrigerer,
    korrigertAv = this.korrigertAv,
    sporsmal = this.sporsmal.map { it.mapSporsmalTilRs() },
    sendtTilArbeidsgiverDato = this.sendtArbeidsgiver,
    arbeidsgiver = map(this.arbeidsgiverNavn, this.arbeidsgiverOrgnummer),
    sykmeldingId = this.sykmeldingId,
    fom = this.fom,
    tom = this.tom,
    startSykeforlop = this.startSykeforlop,
    sykmeldingUtskrevet = this.sykmeldingSkrevet?.toLocalDate(),
    arbeidssituasjon = EnumUtil.konverter(RSArbeidssituasjon::class.java, this.arbeidssituasjon),
    soknadPerioder = this.soknadPerioder?.map { mapSoknadsperiode(it) },
    egenmeldtSykmelding = this.egenmeldtSykmelding,
    merknaderFraSykmelding = this.merknaderFraSykmelding?.map { it.mapMerknad() }
)
