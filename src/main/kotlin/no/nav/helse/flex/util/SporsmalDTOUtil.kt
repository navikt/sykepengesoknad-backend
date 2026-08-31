package no.nav.helse.flex.util

import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO

fun List<SporsmalDTO>?.flatten(): List<SporsmalDTO> =
    (this ?: emptyList()).flatMap { sporsmal ->
        mutableListOf(sporsmal).apply {
            addAll((sporsmal.undersporsmal ?: emptyList()).flatten())
        }
    }

fun List<SporsmalDTO>?.getSporsmalMedTagOrNull(tag: String): SporsmalDTO? =
    this.flatten().firstOrNull { it.tag == tag }

fun List<SporsmalDTO>?.getSporsmalMedTag(tag: String): SporsmalDTO =
    getSporsmalMedTagOrNull(tag)
        ?: throw RuntimeException("Søknaden inneholder ikke spørsmål med tag: $tag")

fun SykepengesoknadDTO.alleSporsmalOgUndersporsmal(): List<SporsmalDTO> =
    sporsmal.flatten()

fun SykepengesoknadDTO.getSporsmalMedTagOrNull(tag: String): SporsmalDTO? =
    sporsmal.getSporsmalMedTagOrNull(tag)

fun SykepengesoknadDTO.getSporsmalMedTag(tag: String): SporsmalDTO =
    getSporsmalMedTagOrNull(tag)
        ?: throw RuntimeException("Søknaden inneholder ikke spørsmål med tag: $tag")
