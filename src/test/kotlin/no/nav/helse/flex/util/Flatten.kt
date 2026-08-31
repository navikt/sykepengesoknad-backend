package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Sporsmal

// Type erasure gjør at denne metoden ikke også kan hete flatten().
fun List<Sporsmal>?.flattenSporsmal(): List<Sporsmal> =
    (this ?: emptyList()).flatMap {
        mutableListOf(it).apply {
            addAll(it.undersporsmal.flattenSporsmal())
        }
    }
