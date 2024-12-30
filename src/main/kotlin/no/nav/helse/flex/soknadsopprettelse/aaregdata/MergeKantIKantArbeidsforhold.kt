package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.*
import java.time.Period

fun List<ArbeidsforholdOversikt>.mergeKantIKant(): List<ArbeidsforholdOversikt> {
    if (this.isEmpty()) return emptyList()

    val grupper = this.groupBy { Pair(it.arbeidssted, it.opplysningspliktig) }

    val mergedResult =
        grupper.flatMap { (_, gruppe) ->
            val sorted = gruppe.sortedBy { it.startdato }

            val mergedGroup = mutableListOf<ArbeidsforholdOversikt>()
            var current = sorted.first()

            for (i in 1 until sorted.size) {
                val next = sorted[i]

                if (canMergeArbeidsforhold(current, next)) {
                    // Oppdater current med slutt, yrke, prosenter fra next
                    current =
                        current.copy(
                            sluttdato = next.sluttdato,
                            yrke = next.yrke,
                            avtaltStillingsprosent = next.avtaltStillingsprosent,
                            permisjonsprosent = next.permisjonsprosent,
                            permitteringsprosent = next.permitteringsprosent,
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
    a: ArbeidsforholdOversikt,
    b: ArbeidsforholdOversikt,
): Boolean {
    // Må ha samme arbeidssted og opplysningspliktig
    if (!harSammeArbeidssted(a.arbeidssted, b.arbeidssted)) return false
    if (!harSammeOpplysningspliktig(a.opplysningspliktig, b.opplysningspliktig)) return false

    // a må ha en sluttdato for å kunne sjekke om b kommer kant i kant
    a.sluttdato ?: return false

    val diffDays = Period.between(a.sluttdato, b.startdato).days
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
