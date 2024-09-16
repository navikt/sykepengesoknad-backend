package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import no.nav.helse.flex.util.erUkedag
import no.nav.helse.flex.util.max
import java.time.LocalDate

fun Sykepengesoknad.hentInntektFraNyttArbeidsforhold(): List<InntektFraNyttArbeidsforholdDTO> {
    val soknad = this

    fun Sporsmal.hentInntektFraNyttArbeidsforhold(): InntektFraNyttArbeidsforholdDTO? {
        fun belopPerDag(
            fom: LocalDate,
            tom: LocalDate,
        ): Int {
            val ferieOgPermisjonsdager =
                samleFravaerListe(soknad)
                    .filter { it.type == FravarstypeDTO.FERIE || it.type == FravarstypeDTO.PERMISJON }
                    .filter { it.fom != null && it.tom != null }
                    .map { it.fom!!.datesUntil(it.tom!!.plusDays(1)).toList() }
                    .flatten()
                    .toSet()

            val belop =
                undersporsmal.firstOrNull { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO }?.forsteSvar?.toDouble()!! / 100

            // Lag liste av alle dager i perioden
            val dager =
                fom.datesUntil(tom.plusDays(1)).toList()
                    .filter { it.erUkedag() }
                    .filter { it !in ferieOgPermisjonsdager }

            if (dager.isEmpty()) return 0

            // Rund ned til nærmeste hele krone
            return (belop / dager.size).toInt()
        }

        if (tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG) {
            if (forsteSvar != "JA") return null

            val forsteArbeidsdag =
                this.undersporsmal.firstOrNull {
                    it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG
                }?.forsteSvar?.tilLocalDate()!!
            val fom = max(soknad.fom!!, forsteArbeidsdag)
            val tom = soknad.tom!!
            return InntektFraNyttArbeidsforholdDTO(
                forstegangssporsmal = true,
                fom = fom,
                tom = tom,
                forsteArbeidsdag = forsteArbeidsdag,
                belopPerDag = belopPerDag(fom, tom),
                arbeidsstedOrgnummer = metadata?.get("arbeidsstedOrgnummer")?.asText()!!,
                opplysningspliktigOrgnummer = metadata.get("opplysningspliktigOrgnummer")?.asText()!!,
            )
        }
        if (tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE) {
            if (forsteSvar != "JA") return null

            return InntektFraNyttArbeidsforholdDTO(
                forstegangssporsmal = false,
                fom = soknad.fom!!,
                tom = soknad.tom!!,
                forsteArbeidsdag = this.metadata?.get("forsteArbeidsdag")?.asText()?.tilLocalDate()!!,
                belopPerDag = belopPerDag(soknad.fom, soknad.tom),
                arbeidsstedOrgnummer = metadata.get("arbeidsstedOrgnummer")?.asText()!!,
                opplysningspliktigOrgnummer = metadata.get("opplysningspliktigOrgnummer")?.asText()!!,
            )
        }
        return null
    }

    return this.sporsmal.mapNotNull { it.hentInntektFraNyttArbeidsforhold() }
}

private fun String?.tilLocalDate(): LocalDate? {
    return this?.let { LocalDate.parse(it) }
}
