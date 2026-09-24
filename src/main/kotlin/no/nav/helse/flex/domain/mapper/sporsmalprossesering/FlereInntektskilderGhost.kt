package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde

fun hentFlereInntektskilderGhost(sykepengesoknad: Sykepengesoknad): Set<KjentInntektskilde> {

    var inntektskilderAareg = sykepengesoknad.arbeidsforholdFraAareg?.map { inntektskilde ->
        KjentInntektskilde(
            navn = inntektskilde.arbeidsstedNavn,
            orgnummer = inntektskilde.arbeidsstedOrgnummer,
            kilde = Kilde.AAAREG
        )
    }

    var inntektskildeKomponent = sykepengesoknad.inntektskilderDataFraInntektskomponenten?.map { inntektskilde ->
        KjentInntektskilde(
            navn = inntektskilde.navn,
            orgnummer = inntektskilde.orgnummer,
            kilde = Kilde.INNTEKTSKOMPONENTEN
        )
    }

    var inntektskilder = buildSet {
        addAll(inntektskildeKomponent.orEmpty())
        addAll(inntektskilderAareg.orEmpty())
    }

    var kilderUtenSykemeldtFra = inntektskilder.filterNot { it.orgnummer==sykepengesoknad.arbeidsgiverOrgnummer}


    return kilderUtenSykemeldtFra.toSet()
}
