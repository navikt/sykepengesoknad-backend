package no.nav.helse.flex.util

import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO

fun List<SporsmalDTO>?.flatten(): List<SporsmalDTO> {
    return (this ?: emptyList()).flatMap {
        mutableListOf(it).apply {
            addAll((it.undersporsmal ?: emptyList()).flatten())
        }
    }
}
