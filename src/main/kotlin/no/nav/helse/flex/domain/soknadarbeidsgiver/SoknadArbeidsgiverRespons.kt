package no.nav.helse.flex.domain.soknadarbeidsgiver

import java.io.Serializable
import java.util.Collections.emptyList

data class SoknadArbeidsgiverRespons(
    val narmesteLedere: List<Soknadrelasjon>? = null,
    val humanResources: List<Soknadrelasjon> = emptyList(),
) : Serializable
