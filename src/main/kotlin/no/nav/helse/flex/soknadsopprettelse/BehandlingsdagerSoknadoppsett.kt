package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon.ANNET
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.rest.SoknadMetadata
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderSelvstendigOgFrilanser
import no.nav.helse.flex.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.helse.flex.soknadsopprettelse.sporsmal.behandlingsdagerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.fraverForBehandling
import no.nav.helse.flex.soknadsopprettelse.sporsmal.vaerKlarOverAtBehandlingsdager
import java.time.Instant
import java.time.LocalDate

fun Sporsmal.plasseringSporsmalBehandlingsdager(): Int {
    return when (this.tag) {
        ANSVARSERKLARING -> -10
        FRAVER_FOR_BEHANDLING -> -9
        ARBEID_UTENFOR_NORGE -> 104
        ANDRE_INNTEKTSKILDER -> 105
        VAER_KLAR_OVER_AT -> 106
        BEKREFT_OPPLYSNINGER -> 107
        else -> plasseringAvSporsmalSomKanRepeteresFlereGanger()
    }
}

fun andreInntekstkilder(soknadMetadata: SoknadMetadata): Sporsmal {
    return when (soknadMetadata.arbeidssituasjon) {
        NAERINGSDRIVENDE -> andreInntektskilderSelvstendigOgFrilanser(soknadMetadata.arbeidssituasjon)
        FRILANSER -> andreInntektskilderSelvstendigOgFrilanser(soknadMetadata.arbeidssituasjon)
        ARBEIDSTAKER -> andreInntektskilderArbeidstaker(soknadMetadata.arbeidsgiverNavn)
        ARBEIDSLEDIG -> andreInntektskilderArbeidsledig(soknadMetadata.fom, soknadMetadata.tom)
        ANNET -> andreInntektskilderArbeidsledig(soknadMetadata.fom, soknadMetadata.tom)
    }
}

private fun Sporsmal.plasseringAvSporsmalSomKanRepeteresFlereGanger(): Int {
    return if (tag.startsWith(ENKELTSTAENDE_BEHANDLINGSDAGER))
        Integer.parseInt(tag.replace(ENKELTSTAENDE_BEHANDLINGSDAGER, ""))
    else 0
}

fun settOppSykepengesoknadBehandlingsdager(
    soknadMetadata: SoknadMetadata,
    erForsteSoknadISykeforlop: Boolean,
    tidligsteFomForSykmelding: LocalDate
): Sykepengesoknad {
    val sporsmal = mutableListOf(
        ansvarserklaringSporsmal(),
        vaerKlarOverAtBehandlingsdager(),
        andreInntekstkilder(soknadMetadata),
        bekreftOpplysningerSporsmal()
    )
        .also {
            if (soknadMetadata.arbeidssituasjon == ARBEIDSTAKER) {
                if (erForsteSoknadISykeforlop) {
                    it.add(fraverForBehandling(soknadMetadata, tidligsteFomForSykmelding))
                }
            }
            if (erForsteSoknadISykeforlop) {
                it.add(arbeidUtenforNorge())
            }
            it.addAll(behandlingsdagerSporsmal(soknadMetadata))
        }

    return Sykepengesoknad(
        soknadstype = Soknadstype.BEHANDLINGSDAGER,
        id = soknadMetadata.id,
        fnr = soknadMetadata.fnr,
        sykmeldingId = soknadMetadata.sykmeldingId,
        status = soknadMetadata.status,
        fom = soknadMetadata.fom,
        tom = soknadMetadata.tom,
        opprettet = Instant.now(),
        startSykeforlop = soknadMetadata.startSykeforlop,
        sykmeldingSkrevet = soknadMetadata.sykmeldingSkrevet,
        arbeidsgiverOrgnummer = soknadMetadata.arbeidsgiverOrgnummer,
        arbeidsgiverNavn = soknadMetadata.arbeidsgiverNavn,
        soknadPerioder = soknadMetadata.sykmeldingsperioder,
        sporsmal = sporsmal,
        opprinnelse = Opprinnelse.SYFOSOKNAD,
        arbeidssituasjon = soknadMetadata.arbeidssituasjon,
        egenmeldtSykmelding = soknadMetadata.egenmeldtSykmelding,
        merknaderFraSykmelding = soknadMetadata.merknader,
    )
}
