package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import no.nav.helse.flex.util.erUkedag
import java.time.LocalDate

fun Sykepengesoknad.hentInntektFraNyttArbeidsforhold(): List<InntektFraNyttArbeidsforholdDTO> {
    val soknad = this

    fun Sporsmal.hentInntektFraNyttArbeidsforhold(): InntektFraNyttArbeidsforholdDTO? {
        data class Belop(
            val virkedager: Int,
            val belopPerDag: Int,
            val belop: Int,
        )

        fun belopPerDag(
            fom: LocalDate,
            tom: LocalDate,
        ): Belop {
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

            if (dager.isEmpty()) return Belop(0, 0, belop.toInt())

            // Rund ned til nærmeste hele krone
            val belopPerDag = (belop / dager.size).toInt()
            return Belop(dager.size, belopPerDag, belop.toInt())
        }

        if (tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS) {
            if (forsteSvar != "JA") return null

            val belopPerDag = belopPerDag(soknad.fom!!, soknad.tom!!)
            return InntektFraNyttArbeidsforholdDTO(
                forstegangssporsmal = false,
                fom = soknad.fom!!,
                tom = soknad.tom!!,
                forsteArbeidsdag = LocalDate.now(),
                belopPerDag = belopPerDag.belopPerDag,
                belop = belopPerDag.belop,
                virkedager = belopPerDag.virkedager,
                arbeidsstedOrgnummer = metadata!!.get("arbeidsstedOrgnummer")?.asText()!!,
                opplysningspliktigOrgnummer = metadata!!.get("opplysningspliktigOrgnummer")?.asText()!!,
            )
        }
        return null
    }

    return this.sporsmal.mapNotNull { it.hentInntektFraNyttArbeidsforhold() }
}
