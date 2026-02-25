package no.nav.helse.flex.domain.sykmelding

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.FiskerBlad
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.util.serialisertTilString
import java.time.LocalDate

fun BrukersituasjonDto.hentArbeidssituasjon(): Arbeidssituasjon =
    when (this.arbeidssituasjon) {
        ArbeidsledigArbeidssituasjonDto.ARBEIDSLEDIG,
        ArbeidsledigArbeidssituasjonDto.PERMITTERT,
        -> { // TODO: Hva skal skje med permittert?
            Arbeidssituasjon.ARBEIDSLEDIG
        }

        ArbeidstakerArbeidssituasjonDto.ARBEIDSTAKER -> {
            Arbeidssituasjon.ARBEIDSTAKER
        }

        ArbeidstakerArbeidssituasjonDto.FISKER_HYRE,
        NaringsdrivendeArbeidssituasjonDto.FISKER_LOTT,
        FiskerLottOgHyreArbeidssituasjonDto.FISKER_LOTT_OG_HYRE,
        -> {
            Arbeidssituasjon.FISKER
        }

        FrilanserArbeidssituasjonDto.FRILANSER -> {
            Arbeidssituasjon.FRILANSER
        }

        NaringsdrivendeArbeidssituasjonDto.NARINGSDRIVENDE -> {
            Arbeidssituasjon.NAERINGSDRIVENDE
        }

        NaringsdrivendeArbeidssituasjonDto.JORDBRUKER -> {
            Arbeidssituasjon.JORDBRUKER
        }

        UkjentYrkesgruppeArbeidssituasjonDto.ANNET,
        UkjentYrkesgruppeArbeidssituasjonDto.UTDATERT,
        -> {
            Arbeidssituasjon.ANNET
        }
    }

fun BrukersituasjonDto.hentArbeidsgiver(): ArbeidsgiverDto? =
    when (this) {
        is ArbeidstakerDto -> this.arbeidsgiver
        is FiskerLottOgHyreDto -> this.arbeidsgiver
        else -> null
    }

fun BrukersituasjonDto.hentTidligereArbeidsgiver(): TidligereArbeidsgiverDto? =
    when (this) {
        is ArbeidsledigDto -> this.tidligereArbeidsgiver
        else -> null
    }

fun BrukersituasjonDto.hentHarOppgittForsikring(): Boolean =
    when (this) {
        is NaringsdrivendeDto -> this.harForsikringForste16Dager
        is FrilanserDto -> this.harForsikringForste16Dager ?: false
        else -> false
    }

fun BrukersituasjonDto.hentEgenmeldingsdager(): List<LocalDate> =
    when (this) {
        is ArbeidstakerDto -> this.egenmeldingsdager
        is FiskerLottOgHyreDto -> this.egenmeldingsdager
        else -> emptyList()
    }

fun BrukersituasjonDto.hentEgenmeldingsdagerSomJsonString(): String? =
    this
        .hentEgenmeldingsdager()
        .ifEmpty { null }
        ?.serialisertTilString()

fun BrukersituasjonDto.hentSykForSykmeldingPerioder(): List<Periode> {
    val perioder =
        when (this) {
            is NaringsdrivendeDto -> this.sykForSykmeldingPerioder
            is FrilanserDto -> this.sykForSykmeldingPerioder
            else -> emptyList()
        }
    return perioder.map { Periode(fom = it.fom, tom = it.tom) }
}

fun BrukersituasjonDto.hentFiskerBlad(): FiskerBlad? =
    when (this) {
        is NaringsdrivendeDto -> this.fiskerSituasjon?.blad?.konverterFikserBlad()
        is ArbeidstakerDto -> this.fiskerSituasjon?.blad?.konverterFikserBlad()
        is FiskerLottOgHyreDto -> this.fiskerSituasjon.blad.konverterFikserBlad()
        else -> null
    }

private fun FiskerBladDto.konverterFikserBlad(): FiskerBlad =
    when (this) {
        FiskerBladDto.A -> FiskerBlad.A
        FiskerBladDto.B -> FiskerBlad.B
    }
