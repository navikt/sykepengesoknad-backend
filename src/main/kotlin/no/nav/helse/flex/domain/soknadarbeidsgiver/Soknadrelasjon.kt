package no.nav.helse.flex.domain.soknadarbeidsgiver

import no.nav.helse.flex.domain.Sykepengesoknad
import java.io.Serializable

data class Soknadrelasjon(
    val fnr: String? = null,
    val orgnummer: String? = null,
    val navn: String? = null,
    val soknader: List<Sykepengesoknad>? = null
) : Serializable
