package no.nav.helse.flex.domain

import no.nav.helse.flex.client.brreg.Rolle
import no.nav.helse.flex.client.brreg.RolleType

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
                            organisasjonsform = rolleTypeTilOrganisasjonsform(it.rolleType),
                        )
                    },
            )

        private fun rolleTypeTilOrganisasjonsform(rolleType: RolleType): String =
            when (rolleType) {
                RolleType.INNH -> "ENK"
                RolleType.DTSO -> "ANS"
                RolleType.DTPR -> "DA"
                RolleType.KOMP -> "KS"
                else -> throw IllegalArgumentException("Ukjent rolleType: $rolleType")
            }
    }
}

data class Organisasjon(
    val orgnummer: String,
    val orgnavn: String,
    val organisasjonsform: String,
)
