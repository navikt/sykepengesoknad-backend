package no.nav.helse.flex.soknadsopprettelse

val uppercaseNavn = setOf("SFO", "AS", "ASA", "NAV")
val lowercaseNavn = setOf("skole")

fun String.prettyOrgnavn(): String {

    fun String.mapEnkeltNavn(): String {
        if (lowercaseNavn.contains(lowercase())) {
            return lowercase()
        }
        if (uppercaseNavn.contains(uppercase())) {
            return uppercase()
        }

        return lowercase().replaceFirstChar { it.uppercase() }
    }

    return this.split(" ").joinToString(" ") { it.mapEnkeltNavn() }
}
