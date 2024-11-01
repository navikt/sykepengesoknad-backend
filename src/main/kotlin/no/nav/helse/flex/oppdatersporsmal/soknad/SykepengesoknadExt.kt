package no.nav.helse.flex.oppdatersporsmal.soknad

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import kotlin.reflect.full.memberProperties

fun Sykepengesoknad.erAvType(vararg typer: Soknadstype): Boolean {
    typer.forEach {
        if (it == this.soknadstype) {
            return true
        }
    }
    return false
}

fun Sykepengesoknad.erIkkeAvType(vararg typer: Soknadstype): Boolean = !erAvType(*typer)

fun Sykepengesoknad.leggTilSporsmaal(sporsmal: Sporsmal): Sykepengesoknad {
    val eksisterendeSpm = this.sporsmal.find { it.tag == sporsmal.tag }
    if (eksisterendeSpm != null) {
        return if (eksisterendeSpm.erUlikUtenomSvarTekstOgId(sporsmal)) {
            // Fjerne spørsmpålet
            this.copy(
                sporsmal = this.sporsmal.filterNot { it.tag == sporsmal.tag } + sporsmal,
            )
        } else {
            this
        }
    }

    return this.copy(sporsmal = this.sporsmal + sporsmal)
}

fun Sykepengesoknad.leggTilSporsmaal(sporsmal: List<Sporsmal>): Sykepengesoknad {
    var soknad = this
    sporsmal.forEach {
        soknad = soknad.leggTilSporsmaal(it)
    }
    return soknad
}

fun List<Sporsmal>.erUlikUtenomSvar(sammenlign: List<Sporsmal>): Boolean {
    fun List<Sporsmal>.flattenOgFjernSvar(): List<Sporsmal> {
        return this.flatten().map { it.copy(svar = emptyList(), undersporsmal = emptyList(), metadata = null) }.sortedBy { it.id }
    }

    val forskjeller = finnForskjellerIListerMedTag(this.flattenOgFjernSvar(), sammenlign.flattenOgFjernSvar())
    val forskjellerAnonymt = forskjeller.map { it.key to it.value?.keys }.toMap().serialisertTilString()
    logger().info("Forskjeller mellom spørsmal i databasen og spørsmål som er besvart: $forskjellerAnonymt")

    return this.flattenOgFjernSvar() != sammenlign.flattenOgFjernSvar()
}

fun <T : Any> finnForskjellIObjekter(
    obj1: T,
    obj2: T,
): Map<String, Pair<Any?, Any?>> {
    require(obj1::class == obj2::class) { "Objects must be of the same class" }

    return obj1::class.memberProperties
        .filter { it.name != "tag" } // Exclude the 'tag' property from comparison
        .mapNotNull { property ->
            val value1 = property.getter.call(obj1)
            val value2 = property.getter.call(obj2)
            if (value1 != value2) {
                property.name to (value1 to value2)
            } else {
                null
            }
        }
        .toMap()
}

fun <T : Any> finnForskjellerIListerMedTag(
    list1: List<T>,
    list2: List<T>,
): Map<String, Map<String, Pair<Any?, Any?>>?> {
    // Create maps based on the 'tag' property for quick lookup
    val map2 = list2.associateBy { it::class.memberProperties.first { it.name == "tag" }.getter.call(it) as String }

    // Compare objects with matching tags from list1 to corresponding items in list2
    return list1.associate { item1 ->
        val tag = item1::class.memberProperties.first { it.name == "tag" }.getter.call(item1) as String
        val item2 = map2[tag]
        tag to if (item2 != null) finnForskjellIObjekter(item1, item2).takeIf { it.isNotEmpty() } else null
    }.filterValues { it != null } // Remove entries with no differences
}

fun List<Sporsmal>.erUlikUtenomSvarTekstOgId(sammenlign: List<Sporsmal>): Boolean {
    fun List<Sporsmal>.flattenOgFjernSvarOgId(): Set<Sporsmal> {
        return this.flatten().map { it.copy(svar = emptyList(), undersporsmal = emptyList(), sporsmalstekst = null, metadata = null) }
            .map { it.copy(id = null) }.toSet()
    }

    val a = this.flattenOgFjernSvarOgId()
    val b = sammenlign.flattenOgFjernSvarOgId()
    return a != b
}

fun Sporsmal.erUlikUtenomSvarTekstOgId(sammenlign: Sporsmal): Boolean = listOf(this).erUlikUtenomSvarTekstOgId(listOf(sammenlign))
