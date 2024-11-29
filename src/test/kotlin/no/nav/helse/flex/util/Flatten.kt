package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO

fun List<SporsmalDTO>?.flatten(): List<SporsmalDTO> {
    return (this ?: emptyList()).flatMap {
        mutableListOf(it).apply {
            addAll((it.undersporsmal ?: emptyList()).flatten())
        }
    }
}

// Type erasure gjør at denne metoden ikke også kan hete flatten().
fun List<Sporsmal>?.flattenSporsmal(): List<Sporsmal> {
    return (this ?: emptyList()).flatMap {
        mutableListOf(it).apply {
            addAll(it.undersporsmal.flattenSporsmal())
        }
    }
}
