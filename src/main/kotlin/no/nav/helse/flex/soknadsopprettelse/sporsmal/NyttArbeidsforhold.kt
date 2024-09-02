package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.toJsonNode

fun nyttArbeidsforholdSporsmal(
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>,
    denneSoknaden: Sykepengesoknad,
    eksisterendeSoknader: List<Sykepengesoknad>,
): List<Sporsmal> {
    val overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner =
        eksisterendeSoknader
            .filter { it.fom != null && it.tom != null }
            .filter { it.arbeidsgiverOrgnummer != denneSoknaden.arbeidsgiverOrgnummer }
            .any { it.tilPeriode().overlapper(denneSoknaden.tilPeriode()) }

    if (overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner) {
        return emptyList()
    }

    val periodeTekst = DatoUtil.formatterPeriode(denneSoknaden.fom!!, denneSoknaden.tom!!)

    return nyeArbeidsforhold.mapNotNull { arbeidsforhold ->

        return@mapNotNull Sporsmal(
            tag = "NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG",
            sporsmalstekst = "Har du startet å jobbe hos ${arbeidsforhold.navn}?",
            undertekst = null,
            svartype = Svartype.JA_NEI,
            min = null,
            max = null,
            metadata = mapOf("orgnummer" to arbeidsforhold.orgnummer, "orgnavn" to arbeidsforhold.navn).toJsonNode(),
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = "NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG",
                        sporsmalstekst = "Når hadde du din første arbeidsdag?",
                        undertekst = null,
                        svartype = Svartype.DATO,
                    ),
                    Sporsmal(
                        tag = "NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO",
                        sporsmalstekst = "Hvor mye har du tjent i perioden $periodeTekst?",
                        undertekst =
                            "Oppgi det du har tjent før skatt. Se på lønnslippen eller kontrakten hvor mye du har tjent eller skal tjene.",
                        svartype = Svartype.BELOP,
                    ),
                ),
        )
    }
}

private fun Sykepengesoknad.tilPeriode(): Periode {
    return Periode(fom!!, tom!!)
}
