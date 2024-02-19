package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Arbeidssituasjon.ANNET
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSLEDIG
import no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER
import no.nav.helse.flex.domain.Arbeidssituasjon.FISKER
import no.nav.helse.flex.domain.Arbeidssituasjon.FRILANSER
import no.nav.helse.flex.domain.Arbeidssituasjon.JORDBRUKER
import no.nav.helse.flex.domain.Arbeidssituasjon.NAERINGSDRIVENDE
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*

fun Sporsmal.plasseringSporsmalBehandlingsdager(): Int {
    return when (this.tag) {
        ANSVARSERKLARING -> -10
        FRAVER_FOR_BEHANDLING -> -9
        FERIE_V2 -> 103
        ARBEID_UTENFOR_NORGE -> 104
        ANDRE_INNTEKTSKILDER -> 105
        VAER_KLAR_OVER_AT -> 106
        BEKREFT_OPPLYSNINGER -> 107
        TIL_SLUTT -> 110
        else -> plasseringAvSporsmalSomKanRepeteresFlereGanger()
    }
}

fun andreInntekstkilder(soknadMetadata: Sykepengesoknad): Sporsmal {
    return when (soknadMetadata.arbeidssituasjon) {
        FISKER,
        JORDBRUKER,
        NAERINGSDRIVENDE,
        -> andreInntektskilderSelvstendigOgFrilanser(NAERINGSDRIVENDE)

        FRILANSER -> andreInntektskilderSelvstendigOgFrilanser(soknadMetadata.arbeidssituasjon)
        ARBEIDSTAKER -> andreInntektskilderArbeidstaker(soknadMetadata.arbeidsgiverNavn)
        ARBEIDSLEDIG -> andreInntektskilderArbeidsledig(soknadMetadata.fom!!, soknadMetadata.tom!!)
        ANNET -> andreInntektskilderArbeidsledig(soknadMetadata.fom!!, soknadMetadata.tom!!)
        null -> throw RuntimeException("Skal ikke skje")
    }
}

private fun Sporsmal.plasseringAvSporsmalSomKanRepeteresFlereGanger(): Int {
    return if (tag.startsWith(ENKELTSTAENDE_BEHANDLINGSDAGER)) {
        Integer.parseInt(tag.replace(ENKELTSTAENDE_BEHANDLINGSDAGER, ""))
    } else {
        0
    }
}

fun settOppSykepengesoknadBehandlingsdager(opts: SettOppSoknadOptions): List<Sporsmal> {
    val (sykepengesoknad, erForsteSoknadISykeforlop, _, _) = opts

    return mutableListOf(
        ansvarserklaringSporsmal(),
        andreInntekstkilder(sykepengesoknad),
    )
        .also {
            if (erForsteSoknadISykeforlop) {
                it.add(arbeidUtenforNorge())
            }
            if (sykepengesoknad.arbeidssituasjon == ARBEIDSTAKER) {
                it.add(ferieSporsmal(sykepengesoknad.fom!!, sykepengesoknad.tom!!))
            }
            it.addAll(behandlingsdagerSporsmal(sykepengesoknad))
            it.add(tilSlutt())
        }.toList()
}
