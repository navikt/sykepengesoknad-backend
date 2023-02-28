package no.nav.helse.flex.soknadsopprettelse

val uppercaseNavn =
    setOf("SFO", "AS", "ASA", "NAV", "SA", "HF", "BUP", "VVS", "KF", "IKS", "DNB", "DNV", "A/S", "DA", "DPS")
val lowercaseNavn = setOf(
    "skole",
    "skule",
    "ungdomsskole",
    "kommune",
    "barneskole",
    "barnehage",
    "avd",
    "avdeling",
    "og",
    "i",
    "lager",
    "universitetssykehus",
    "omsorgssenter",
    "fakultet",
    "entreprenør",
    "legevakt",
    "sykehjem",
    "videregående"
)

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

    return this
        .split(",")
        .map { it.trim() }
        .map {
            it.split(" ")
                .joinToString(" ") { it.mapEnkeltNavn() }
        }
        .joinToString(", ")
}
