package no.nav.helse.flex.client.pdl

const val AKTORID = "AKTORID"
const val FOLKEREGISTERIDENT = "FOLKEREGISTERIDENT"

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ResponseData(
    val hentIdenter: HentIdenter? = null
)

data class HentIdenter(
    val identer: List<PdlIdent>
)

data class PdlIdent(val gruppe: String, val ident: String)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)
