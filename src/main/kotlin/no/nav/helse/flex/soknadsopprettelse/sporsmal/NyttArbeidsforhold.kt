package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.*
import java.time.LocalDate

fun nyttArbeidsforholdSporsmal(
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>?,
    denneSoknaden: Sykepengesoknad,
    oppdatertTom: LocalDate? = null,
): List<Sporsmal> {
    return nyeArbeidsforhold
        ?.filter { it.startdato.isBeforeOrEqual(oppdatertTom ?: denneSoknaden.tom!!) }
        ?.filter {
            if (it.sluttdato == null) {
                return@filter true
            }
            val afterOrEqual = it.sluttdato.isAfterOrEqual(denneSoknaden.fom!!)
            return@filter afterOrEqual
        }
        ?.toSet()
        ?.map { arbeidsforhold ->

            val fom = max(denneSoknaden.fom!!, arbeidsforhold.startdato)
            val tom = oppdatertTom ?: denneSoknaden.tom!!

            val periodeTekst = DatoUtil.formatterPeriode(fom, tom)
            val metadata =
                mapOf(
                    "arbeidsstedOrgnummer" to arbeidsforhold.arbeidsstedOrgnummer,
                    "arbeidsstedNavn" to arbeidsforhold.arbeidsstedNavn,
                    "opplysningspliktigOrgnummer" to arbeidsforhold.opplysningspliktigOrgnummer,
                    "startdatoAareg" to arbeidsforhold.startdato.toString(),
                    "fom" to fom.toString(),
                    "tom" to tom.toString(),
                )
            return@map Sporsmal(
                tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS,
                sporsmalstekst = "Har du jobbet noe hos ${arbeidsforhold.arbeidsstedNavn} i perioden $periodeTekst?",
                undertekst = null,
                svartype = Svartype.JA_NEI,
                min = fom.toString(),
                max = tom.toString(),
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
        } ?: emptyList()
}
