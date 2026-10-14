package no.nav.helse.flex.mockdispatcher

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.GetPersonResponse
import no.nav.helse.flex.client.pdl.HentIdenter
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.pdl.ResponseData
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import tools.jackson.module.kotlin.readValue

object PdlMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val graphReq: PdlClient.GraphQLRequest = objectMapper.readValue(request.body!!.utf8())
        val ident = graphReq.variables["ident"] ?: return MockResponse(code = 400, body = "Ingen ident variabel")

        if (ident.startsWith("2")) {
            return skapResponse(listOf(ident, ident.replaceFirstChar { "1" }))
        }
        if (ident.startsWith("3")) {
            return skapResponse(listOf(ident, ident.replaceFirstChar { "1" }, ident.replaceFirstChar { "2" }))
        }
        return skapResponse(listOf(ident))
    }

    fun skapResponse(identer: List<String>): MockResponse {
        val pdlIdenter =
            identer
                .map { PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = it) }
                .toMutableList()
                .also { it.add(PdlIdent(gruppe = AKTORID, ident = identer.first() + "00")) }

        return MockResponse(
            body =
                GetPersonResponse(
                    data = ResponseData(hentIdenter = HentIdenter(identer = pdlIdenter)),
                    errors = null,
                ).serialisertTilString(),
        )
    }
}
