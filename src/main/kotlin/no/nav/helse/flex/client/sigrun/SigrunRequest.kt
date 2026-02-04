package no.nav.helse.flex.client.sigrun

data class SigrunRequest(
    val personident: String,
    val inntektsaar: String,
    val rettighetspakke: String,
)
