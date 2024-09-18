package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.toJsonNode
import java.time.format.DateTimeFormatter

fun nyttArbeidsforholdSporsmal(
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>,
    denneSoknaden: Sykepengesoknad,
    eksisterendeSoknader: () -> List<Sykepengesoknad>,
): List<Sporsmal> {
    if (nyeArbeidsforhold.isEmpty()) {
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

    val periodeTekst = DatoUtil.formatterPeriode(denneSoknaden.fom!!, denneSoknaden.tom!!)

    return nyeArbeidsforhold.map { arbeidsforhold ->

        val harAlleredeStartet =
            hentedeEksisterendeSoknader
                .filter { it.startSykeforlop == denneSoknaden.startSykeforlop }
                .filter { it.status == Soknadstatus.SENDT }
                .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
                .filter { it.fom!!.isBefore(denneSoknaden.fom) }
                .flatMap { it.sporsmal }
                .filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG }
                .filter { it.metadata!!["arbeidsstedOrgnummer"].asText() == arbeidsforhold.arbeidsstedOrgnummer }
                .firstOrNull { it.forsteSvar == "JA" }

        val metadata =
            mapOf(
                "arbeidsstedOrgnummer" to arbeidsforhold.arbeidsstedOrgnummer,
                "arbeidsstedNavn" to arbeidsforhold.arbeidsstedNavn,
                "opplysningspliktigOrgnummer" to arbeidsforhold.opplysningspliktigOrgnummer,
            )
        if (harAlleredeStartet != null) {
            return@map Sporsmal(
                tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE,
                sporsmalstekst = "Har du jobbet noe hos ${arbeidsforhold.arbeidsstedNavn} i perioden $periodeTekst?",
                undertekst = null,
                svartype = Svartype.JA_NEI,
                min = null,
                max = null,
                metadata =
                    metadata.toMutableMap().also { metadata ->
                        metadata["forsteArbeidsdag"] =
                            harAlleredeStartet
                                .undersporsmal
                                .first { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG }
                                .forsteSvar!!
                    }.toJsonNode(),
                kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
                undersporsmal =
                    listOf(
                        bruttoInntektSporsmal(periodeTekst),
                    ),
            )
        }

        return@map Sporsmal(
            tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG,
            sporsmalstekst = "Har du startet å jobbe hos ${arbeidsforhold.arbeidsstedNavn}?",
            undertekst = null,
            svartype = Svartype.JA_NEI,
            min = null,
            max = null,
            metadata = metadata.toJsonNode(),
            kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
            undersporsmal =
                listOf(
                    Sporsmal(
                        tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG,
                        sporsmalstekst = "Når hadde du din første arbeidsdag?",
                        undertekst = null,
                        svartype = Svartype.DATO,
                        max = denneSoknaden.tom.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    ),
                    bruttoInntektSporsmal(periodeTekst),
                ),
        )
    }
}

private fun bruttoInntektSporsmal(periodeTekst: String) =
    Sporsmal(
        tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO,
        sporsmalstekst = "Hvor mye har du tjent i perioden $periodeTekst?",
        undertekst =
            "Oppgi det du har tjent før skatt. " +
                "Se på lønnslippen eller kontrakten hvor mye du har tjent eller skal tjene.",
        svartype = Svartype.BELOP,
    )

private fun Sykepengesoknad.tilPeriode(): Periode {
    return Periode(fom!!, tom!!)
}
