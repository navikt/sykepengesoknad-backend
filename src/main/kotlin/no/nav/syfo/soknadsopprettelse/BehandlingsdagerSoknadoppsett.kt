package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Arbeidssituasjon.ANNET
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.syfo.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.syfo.domain.Arbeidssituasjon.FRILANSER
import no.nav.syfo.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.syfo.domain.Opprinnelse
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.rest.SoknadMetadata
import no.nav.syfo.soknadsopprettelse.sporsmal.andreInntektskilderArbeidsledig
import no.nav.syfo.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstaker
import no.nav.syfo.soknadsopprettelse.sporsmal.andreInntektskilderSelvstendigOgFrilanser
import no.nav.syfo.soknadsopprettelse.sporsmal.ansvarserklaringSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.arbeidUtenforNorge
import no.nav.syfo.soknadsopprettelse.sporsmal.behandlingsdagerSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.bekreftOpplysningerSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.fraverForBehandling
import no.nav.syfo.soknadsopprettelse.sporsmal.vaerKlarOverAtBehandlingsdager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime.now

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
