package no.nav.helse.flex.domain

import no.nav.helse.flex.client.brreg.Rolle

class SelvstendigNaringsdrivende(
    val organisasjoner: List<Organisasjon>,
) {
    companion object {
        fun mapFraRoller(roller: List<Rolle>) =
            SelvstendigNaringsdrivende(
                organisasjoner =
                    roller.map {
                        Organisasjon(
                            orgnummer = it.organisasjonsnummer,
                            orgnavn = it.organisasjonsnavn,
                            rolletype = it.rolletype.name,
                        )
                    },
            )
    }
}

data class Organisasjon(
    val orgnummer: String,
    val orgnavn: String,
    val rolletype: String,
)
