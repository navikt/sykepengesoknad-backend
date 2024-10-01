package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO

fun Sykepengesoknad.hentInntektFraNyttArbeidsforhold(): List<InntektFraNyttArbeidsforholdDTO> {
    val soknad = this

    fun Sporsmal.hentInntektFraNyttArbeidsforhold(): InntektFraNyttArbeidsforholdDTO? {
        if (tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS) {
            if (forsteSvar == "JA") {
                val belopSvar = undersporsmal.firstOrNull { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO }?.forsteSvar
                val belop = (belopSvar?.toDouble()!! / 100).toInt()
                return InntektFraNyttArbeidsforholdDTO(
                    fom = soknad.fom!!,
                    tom = soknad.tom!!,
                    belop = belop,
                    arbeidsstedOrgnummer = metadata!!.get("arbeidsstedOrgnummer")?.asText()!!,
                    opplysningspliktigOrgnummer = metadata!!.get("opplysningspliktigOrgnummer")?.asText()!!,
                    harJobbet = true,
                )
            }
            if (forsteSvar == "NEI") {
                return InntektFraNyttArbeidsforholdDTO(
                    fom = soknad.fom!!,
                    tom = soknad.tom!!,
                    belop = null,
                    arbeidsstedOrgnummer = metadata!!.get("arbeidsstedOrgnummer")?.asText()!!,
                    opplysningspliktigOrgnummer = metadata!!.get("opplysningspliktigOrgnummer")?.asText()!!,
                    harJobbet = false,
                )
            }
        }
        return null
    }

    return this.sporsmal.mapNotNull { it.hentInntektFraNyttArbeidsforhold() }
}
