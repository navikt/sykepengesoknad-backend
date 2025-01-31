package no.nav.helse.flex.controller.mapper

import no.nav.helse.flex.controller.domain.sykepengesoknad.*
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.util.EnumUtil
import no.nav.helse.flex.util.tilLocalDate
import no.nav.helse.flex.util.tilOsloLocalDateTime
import java.util.Optional.ofNullable

private fun mapSvarTilRSSvar(svar: Svar): RSSvar {
    return RSSvar(
        id = svar.id,
        verdi = svar.verdi,
    )
}

private fun mapSoknadsperiode(soknadsperiode: Soknadsperiode): RSSoknadsperiode {
    return RSSoknadsperiode(
        fom = soknadsperiode.fom,
        tom = soknadsperiode.tom,
        grad = soknadsperiode.grad,
        sykmeldingstype = mapSykmeldingstype(soknadsperiode.sykmeldingstype),
    )
}

private fun mapSykmeldingstype(sykmeldingstype: Sykmeldingstype?): RSSykmeldingstype? {
    return if (sykmeldingstype == null) {
        null
    } else {
        RSSykmeldingstype.valueOf(sykmeldingstype.name)
    }
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
        kriterieForVisningAvUndersporsmal =
            EnumUtil.konverter(
                RSVisningskriterie::class.java,
                this.kriterieForVisningAvUndersporsmal,
            ),
        svar = this.svar.map { mapSvarTilRSSvar(it) },
        undersporsmal = this.undersporsmal.map { it.mapSporsmalTilRs() },
        metadata = this.metadata,
    )
}

private fun map(
    arbeidsgiverNavn: String?,
    arbeidsgiverOrgnummer: String?,
): RSArbeidsgiver? {
    return if (arbeidsgiverNavn == null || arbeidsgiverOrgnummer == null) {
        null
    } else {
        RSArbeidsgiver(
            navn = arbeidsgiverNavn,
            orgnummer = arbeidsgiverOrgnummer,
        )
    }
}

fun konverterSoknadstatus(soknadstatus: Soknadstatus): RSSoknadstatus {
    return if (soknadstatus === Soknadstatus.UTGATT) {
        RSSoknadstatus.UTGAATT
    } else {
        EnumUtil.konverter(RSSoknadstatus::class.java, soknadstatus)
    }
}

private fun Merknad.mapMerknad(): RSMerknad {
    return RSMerknad(
        type = this.type,
        beskrivelse = this.beskrivelse,
    )
}

fun Sykepengesoknad.tilRSSykepengesoknad() =
    RSSykepengesoknad(
        id = this.id,
        soknadstype = EnumUtil.konverter(RSSoknadstype::class.java, this.soknadstype),
        status = konverterSoknadstatus(this.status),
        opprettetDato = this.opprettet?.tilLocalDate(),
        avbruttDato = this.avbruttDato,
        sendtTilNAVDato = this.sendtNav?.tilOsloLocalDateTime(),
        sendtTilArbeidsgiverDato = this.sendtArbeidsgiver?.tilOsloLocalDateTime(),
        korrigerer = this.korrigerer,
        korrigertAv = this.korrigertAv,
        sporsmal = this.sporsmal.map { it.mapSporsmalTilRs() },
        arbeidsgiver = map(this.arbeidsgiverNavn, this.arbeidsgiverOrgnummer),
        sykmeldingId = this.sykmeldingId,
        fom = this.fom,
        tom = this.tom,
        startSykeforlop = this.startSykeforlop,
        sykmeldingUtskrevet = this.sykmeldingSkrevet?.tilLocalDate(),
        arbeidssituasjon = EnumUtil.konverter(RSArbeidssituasjon::class.java, this.arbeidssituasjon),
        soknadPerioder = this.soknadPerioder?.map { mapSoknadsperiode(it) },
        egenmeldtSykmelding = this.egenmeldtSykmelding,
        merknaderFraSykmelding = this.merknaderFraSykmelding?.map { it.mapMerknad() },
        opprettetAvInntektsmelding = this.opprettetAvInntektsmelding,
        utenlandskSykmelding = this.utenlandskSykmelding,
        klippet = this.klippet,
        inntektskilderDataFraInntektskomponenten = this.inntektskilderDataFraInntektskomponenten,
        korrigeringsfristUtlopt = this.korrigeringsfristUtlopt,
        inntektsopplysningerNyKvittering = this.inntektsopplysningerNyKvittering,
        inntektsopplysningerInnsendingId = this.inntektsopplysningerInnsendingId,
        inntektsopplysningerInnsendingDokumenter = this.inntektsopplysningerInnsendingDokumenter?.map { it.tittel },
        forstegangssoknad = this.forstegangssoknad,
        kjentOppholdstillatelse = this.kjentOppholdstillatelse,
        julesoknad = this.julesoknad,
        friskTilArbeidVedtakId = this.friskTilArbeidVedtakId,
    )

fun Sykepengesoknad.tilRSSykepengesoknadMetadata() =
    RSSykepengesoknadMetadata(
        id = this.id,
        sykmeldingId = this.sykmeldingId,
        soknadstype = EnumUtil.konverter(RSSoknadstype::class.java, this.soknadstype),
        status = konverterSoknadstatus(this.status),
        fom = this.fom,
        tom = this.tom,
        opprettetDato = this.opprettet?.tilLocalDate(),
        sendtTilNAVDato = ofNullable(this.sendtNav?.tilOsloLocalDateTime()).orElse(null),
        sendtTilArbeidsgiverDato = this.sendtArbeidsgiver?.tilOsloLocalDateTime(),
        avbruttDato = this.avbruttDato,
        startSykeforlop = this.startSykeforlop,
        sykmeldingUtskrevet = this.sykmeldingSkrevet?.tilLocalDate(),
        arbeidsgiver = map(this.arbeidsgiverNavn, this.arbeidsgiverOrgnummer),
        korrigerer = this.korrigerer,
        korrigertAv = this.korrigertAv,
        arbeidssituasjon = EnumUtil.konverter(RSArbeidssituasjon::class.java, this.arbeidssituasjon),
        soknadPerioder = this.soknadPerioder?.map { mapSoknadsperiode(it) },
        egenmeldtSykmelding = this.egenmeldtSykmelding,
        merknaderFraSykmelding = this.merknaderFraSykmelding?.map { it.mapMerknad() },
        opprettetAvInntektsmelding = this.opprettetAvInntektsmelding,
        forstegangssoknad = this.forstegangssoknad,
        friskTilArbeidVedtakId = this.friskTilArbeidVedtakId,
    )

fun Sykepengesoknad.tilRSSykepengesoknadFlexInternal() =
    RSSykepengesoknadFlexInternal(
        id = this.id,
        sykmeldingId = this.sykmeldingId,
        soknadstype = EnumUtil.konverter(RSSoknadstype::class.java, this.soknadstype),
        status = konverterSoknadstatus(this.status),
        fom = this.fom,
        tom = this.tom,
        opprettetDato = this.opprettet?.tilOsloLocalDateTime(),
        sendtTilNAVDato = this.sendtNav?.tilOsloLocalDateTime(),
        sendtTilArbeidsgiverDato = this.sendtArbeidsgiver?.tilOsloLocalDateTime(),
        avbruttDato = this.avbruttDato,
        startSykeforlop = this.startSykeforlop,
        sykmeldingUtskrevet = this.sykmeldingSkrevet?.tilOsloLocalDateTime(),
        sykmeldingSignaturDato = this.sykmeldingSignaturDato?.tilOsloLocalDateTime(),
        arbeidsgiverNavn = this.arbeidsgiverNavn,
        arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
        korrigerer = this.korrigerer,
        korrigertAv = this.korrigertAv,
        arbeidssituasjon = EnumUtil.konverter(RSArbeidssituasjon::class.java, this.arbeidssituasjon),
        soknadPerioder = this.soknadPerioder?.map { mapSoknadsperiode(it) },
        merknaderFraSykmelding = this.merknaderFraSykmelding?.map { it.mapMerknad() },
    )
