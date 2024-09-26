package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.toJsonNode

fun nyttArbeidsforholdSporsmal(
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>?,
    denneSoknaden: Sykepengesoknad,
    eksisterendeSoknader: () -> List<Sykepengesoknad>,
): List<Sporsmal> {
    if (nyeArbeidsforhold.isNullOrEmpty()) {
        return emptyList()
    }

    val hentedeEksisterendeSoknader = eksisterendeSoknader()
    val overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner =
        hentedeEksisterendeSoknader
            .filter { it.fom != null && it.tom != null }
            .filter { it.arbeidsgiverOrgnummer != denneSoknaden.arbeidsgiverOrgnummer }
            .any { it.tilPeriode().overlapper(denneSoknaden.tilPeriode()) }

    if (overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner) {
        return emptyList()
    }

    return nyeArbeidsforhold.map { arbeidsforhold ->

        val metadata =
            mapOf(
                "arbeidsstedOrgnummer" to arbeidsforhold.arbeidsstedOrgnummer,
                "arbeidsstedNavn" to arbeidsforhold.arbeidsstedNavn,
                "opplysningspliktigOrgnummer" to arbeidsforhold.opplysningspliktigOrgnummer,
            )
        val periodeTekst = DatoUtil.formatterPeriode(denneSoknaden.fom!!, denneSoknaden.tom!!)
        return@map Sporsmal(
            tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS,
            sporsmalstekst = "Har du jobbet noe hos ${arbeidsforhold.arbeidsstedNavn} i perioden $periodeTekst?",
            undertekst = null,
            svartype = Svartype.JA_NEI,
            min = null,
            max = null,
            metadata =
                metadata.toJsonNode(),
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO,
                        sporsmalstekst = "Hvor mye har du tjent i perioden $periodeTekst?",
                        undertekst =
                            "Oppgi det du har tjent før skatt. " +
                                "Se på lønnslippen eller kontrakten hvor mye du har tjent eller skal tjene.",
                        svartype = Svartype.BELOP,
                    ),
                ),
        )
    }
}

private fun Sykepengesoknad.tilPeriode(): Periode {
    return Periode(fom!!, tom!!)
}
