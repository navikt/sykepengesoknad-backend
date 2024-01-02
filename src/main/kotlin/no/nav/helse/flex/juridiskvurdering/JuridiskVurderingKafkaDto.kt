package no.nav.helse.flex.juridiskvurdering

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.time.LocalDate

data class JuridiskVurderingKafkaDto(
    val id: String,
    val versjon: String,
    val eventName: String,
    val kilde: String,
    val versjonAvKode: String,
    val fodselsnummer: String,
    val sporing: Map<SporingType, List<String>>,
    val tidsstempel: Instant,
    val lovverk: String,
    val lovverksversjon: LocalDate,
    val paragraf: String,
    val ledd: Int?,
    val punktum: Int?,
    val bokstav: String?,
    val input: Map<String, Any>,
    val output: Map<String, Any>?,
    val utfall: Utfall,
)

enum class Utfall {
    VILKAR_OPPFYLT,
    VILKAR_IKKE_OPPFYLT,
    VILKAR_UAVKLART,
    VILKAR_BEREGNET,
}

enum class SporingType {
    @JsonProperty("organisasjonsnummer")
    ORGANISASJONSNUMMER,

    @JsonProperty("soknad")
    SOKNAD,

    @JsonProperty("sykmelding")
    SYKMELDING,

    @JsonProperty("vedtaksperiode")
    VEDTAKSPERIODE,

    @JsonProperty("inntektsmelding")
    INNTEKTSMELDING,

    @JsonProperty("utbetaling")
    UTBETALING,
}
