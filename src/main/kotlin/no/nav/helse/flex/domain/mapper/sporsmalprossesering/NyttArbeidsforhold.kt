package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import java.time.LocalDate

fun Sykepengesoknad.hentInntektFraNyttArbeidsforhold(): List<InntektFraNyttArbeidsforholdDTO> {
    fun Sporsmal.hentInntektFraNyttArbeidsforhold(): InntektFraNyttArbeidsforholdDTO? {
        if (tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)) {
            if (metadata == null) {
                return null
            }
            if (forsteSvar == "JA") {
                val belopSvar = undersporsmal.firstOrNull { it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO) }?.forsteSvar
                val belop = (belopSvar?.toDouble()!! / 100).toInt()
                return InntektFraNyttArbeidsforholdDTO(
                    fom = metadata.get("fom")?.asText()!!.tilLocalDate(),
                    tom = metadata.get("tom")?.asText()!!.tilLocalDate(),
                    belop = belop,
                    arbeidsstedOrgnummer = metadata.get("arbeidsstedOrgnummer")?.asText()!!,
                    opplysningspliktigOrgnummer = metadata.get("opplysningspliktigOrgnummer")?.asText()!!,
                    harJobbet = true,
                )
            }
            if (forsteSvar == "NEI") {
                return InntektFraNyttArbeidsforholdDTO(
                    fom = metadata.get("fom")?.asText()!!.tilLocalDate(),
                    tom = metadata.get("tom")?.asText()!!.tilLocalDate(),
                    belop = null,
                    arbeidsstedOrgnummer = metadata.get("arbeidsstedOrgnummer")?.asText()!!,
                    opplysningspliktigOrgnummer = metadata.get("opplysningspliktigOrgnummer")?.asText()!!,
                    harJobbet = false,
                )
            }
        }
        return null
    }

    return this.sporsmal.mapNotNull { it.hentInntektFraNyttArbeidsforhold() }
}

private fun String.tilLocalDate(): LocalDate = LocalDate.parse(this)
