package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.mapper.tilKjentInntektskildeDTO
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sjekkGhostInntekter
import no.nav.helse.flex.soknadsopprettelse.sjekkNyeArbeidsforhold
import no.nav.helse.flex.sykepengesoknad.kafka.KjenteInntektskilderDTO
import java.time.LocalDate

fun hentFlereInntektskilderGhost(
    fom: LocalDate,
    tom: LocalDate,
    arbeidsforholdFraAareg: List<ArbeidsforholdFraAAreg>?,
    inntektsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>?,
    arbeidsgiverOrgnummer: String?,
): Set<KjenteInntektskilderDTO> {
    val heltNyeArbeidsforhold =
        sjekkNyeArbeidsforhold(
            fom = fom,
            tom = tom,
            arbeidforholdOversikt = arbeidsforholdFraAareg,
        )?.toList()

    if (!arbeidsforholdFraAareg.isNullOrEmpty() || !inntektsforholdFraInntektskomponenten.isNullOrEmpty()) {
        val ghostInntekter =
            sjekkGhostInntekter(
                arbeidsforholdFraInntektskomponenten =
                    inntektsforholdFraInntektskomponenten
                        ?: emptyList(),
                arbeidforholdOversikt = arbeidsforholdFraAareg ?: emptyList(),
                arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
                nyeArbeidsforhold = heltNyeArbeidsforhold ?: emptyList(),
            )

        return ghostInntekter.map { it.tilKjentInntektskildeDTO() }.toSet()
    }

    return emptySet()
}
