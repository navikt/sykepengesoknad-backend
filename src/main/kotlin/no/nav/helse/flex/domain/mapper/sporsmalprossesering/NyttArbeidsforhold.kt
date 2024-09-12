package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import no.nav.helse.flex.util.max
import java.time.LocalDate

fun Sykepengesoknad.hentInntektFraNyttArbeidsforhold(): List<InntektFraNyttArbeidsforholdDTO>? {
    if (status != Soknadstatus.SENDT) return null
    val soknad = this

    fun Sporsmal.hentInntektFraNyttArbeidsforhold(): InntektFraNyttArbeidsforholdDTO? {
        fun belopPerDag(
            fom: LocalDate,
            tom: LocalDate,
        ): Int {
            val antallDager = tom.toEpochDay() - fom.toEpochDay() + 1
            val belop = undersporsmal.firstOrNull { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO }?.forsteSvar?.toDouble()!!

            // TODO hensynta helg og ferie

            // Rund ned til nærmeste hele krone
            return (belop / antallDager).toInt()
        }

        if (tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG) {
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
