package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import java.time.Period

fun List<Arbeidsforhold>.mergeKantIKant(): List<Arbeidsforhold> {
    if (this.isEmpty()) return emptyList()

    val grupper = this.groupBy { Pair(it.arbeidssted, it.opplysningspliktig) }

    val mergedResult =
        grupper.flatMap { (_, gruppe) ->
            val sorted = gruppe.sortedBy { it.ansettelsesperiode.startdato }

            val mergedGroup = mutableListOf<Arbeidsforhold>()
            var current = sorted.first()

            for (i in 1 until sorted.size) {
                val next = sorted[i]

                if (canMergeArbeidsforhold(current, next)) {
                    // Oppdater current med slutt, yrke, prosenter fra next
                    current =
                        current.copy(
                            ansettelsesperiode =
                                Ansettelsesperiode(
                                    sluttdato = next.ansettelsesperiode.sluttdato,
                                    startdato = current.ansettelsesperiode.startdato,
                                ),
                        )
                } else {
                    mergedGroup.add(current)
                    current = next
                }
            }

            mergedGroup.add(current)
            mergedGroup
        }

    return mergedResult
}

private fun canMergeArbeidsforhold(
    a: Arbeidsforhold,
    b: Arbeidsforhold,
): Boolean {
    // Må ha samme arbeidssted og opplysningspliktig
    if (!harSammeArbeidssted(a.arbeidssted, b.arbeidssted)) return false
    if (!harSammeOpplysningspliktig(a.opplysningspliktig, b.opplysningspliktig)) return false

    // a må ha en sluttdato for å kunne sjekke om b kommer kant i kant
    a.ansettelsesperiode.sluttdato ?: return false

    val diffDays = Period.between(a.ansettelsesperiode.sluttdato, b.ansettelsesperiode.startdato).days
    return diffDays >= 0 && diffDays <= 1
}

private fun harSammeArbeidssted(
    a: Arbeidssted,
    b: Arbeidssted,
): Boolean {
    if (a.type != b.type) return false
    return a.identer.toSet() == b.identer.toSet()
}

private fun harSammeOpplysningspliktig(
    a: Opplysningspliktig,
    b: Opplysningspliktig,
): Boolean {
    if (a.type != b.type) return false
    return a.identer.toSet() == b.identer.toSet()
}
