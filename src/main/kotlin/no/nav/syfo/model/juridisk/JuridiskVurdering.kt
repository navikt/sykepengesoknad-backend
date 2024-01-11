package no.nav.syfo.model.juridisk

import java.time.LocalDate
import java.time.ZonedDateTime

data class JuridiskVurdering(
    val id: String,
    val eventName: String,
    val version: String,
    val kilde: String,
    val versjonAvKode: String,
    val fodselsnummer: String,
    val juridiskHenvisning: JuridiskHenvisning,
    val sporing: Map<String, String>,
    val input: Map<String, Any>,
    val tidsstempel: ZonedDateTime,
    val utfall: JuridiskUtfall
)

data class JuridiskVurderingResult(
    val juridiskeVurderinger: List<JuridiskVurdering>
)

data class JuridiskHenvisning(
    val lovverk: Lovverk,
    val paragraf: String,
    val ledd: Int?,
    val punktum: Int?,
    val bokstav: String?,
)

enum class Lovverk(
    val navn: String,
    val kortnavn: String,
    val lovverksversjon: LocalDate
){
    FOLKETRYGDLOVEN(
        navn = "Lov om folketrygd",
        kortnavn = "Folketrygdloven",
        lovverksversjon = LocalDate.of(2022,1,1)
    ),
    FORVALTNINGSLOVEN(
        navn = "Lov om behandlingsmåten i forvaltningssaker",
        kortnavn = "Forvaltningsloven",
        lovverksversjon = LocalDate.of(2022, 1, 1)
    ),
    HELSEPERSONELLOVEN(
      navn = "Lov om helsepersonell m.v.",
        kortnavn = "Helsepersonelloven",
        lovverksversjon = LocalDate.of(2022, 1, 1)
    )
}

enum class JuridiskUtfall {
    VILKAR_OPPFYLT,
    VILKAR_IKKE_OPPFYLT,
    VILKAR_UAVKLART,
    VILKAR_BEREGNET
}
