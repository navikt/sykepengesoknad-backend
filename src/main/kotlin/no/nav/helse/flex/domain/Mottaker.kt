package no.nav.helse.flex.domain

enum class Mottaker {
    NAV,
    ARBEIDSGIVER,
    ARBEIDSGIVER_OG_NAV,
    ;

    fun tilNav(): Boolean =
        when (this) {
            NAV -> true
            ARBEIDSGIVER_OG_NAV -> true
            ARBEIDSGIVER -> false
        }
}
