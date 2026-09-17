package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.andreInntektskilderArbeidstakerV2
import no.nav.helse.flex.soknadsopprettelse.sporsmal.flereInntektskilderGhost

fun settOppSporsmalOmAndreArbeidsforhold(
    sykepengesoknad: Sykepengesoknad,
    andreKjenteArbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    arbeidsforholdoversiktResponse: List<ArbeidsforholdFraAAreg>?,
): Sporsmal {
    val antallArbeidsforhold = andreKjenteArbeidsforholdFraInntektskomponenten.size + (arbeidsforholdoversiktResponse?.size ?: 0)

    return if (antallArbeidsforhold > 1) {
        flereInntektskilderGhost(
            sykmeldingOrgnavn = sykepengesoknad.arbeidsgiverNavn,
            sykmeldingOrgnr = sykepengesoknad.arbeidsgiverOrgnummer,
            andreKjenteArbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforholdFraInntektskomponenten,
            nyeArbeidsforholdFraAareg = arbeidsforholdoversiktResponse,
            soknadsperiode =
                Soknadsperiode(
                    fom = sykepengesoknad.fom!!,
                    tom = sykepengesoknad.tom!!,
                    grad = 0,
                    sykmeldingstype = null,
                ),
        )
    } else {
        andreInntektskilderArbeidstakerV2(
            sykmeldingOrgnavn = sykepengesoknad.arbeidsgiverNavn,
            sykmeldingOrgnr = sykepengesoknad.arbeidsgiverOrgnummer,
            andreKjenteArbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforholdFraInntektskomponenten,
            nyeArbeidsforholdFraAareg = arbeidsforholdoversiktResponse,
        )
    }
}
